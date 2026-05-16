/*
 * Copyright (C) 2021 - 2024 Vishnu Sanal. T
 *
 * This file is part of DialogMusicPlayer.
 *
 * DialogMusicPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package phone.vishnu.dialogmusicplayer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

class MediaPlaybackService :
    MediaBrowserServiceCompat(),
    AudioManager.OnAudioFocusChangeListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaPlayer: MediaPlayer
    private var audio: Audio? = null

    private var isPlayingOnceInProgress = false
    private var wasPlayingWhenLosingAudioFocus = false
    private var isPlayerReleased = false

    private var audioFocusRequest: AudioFocusRequest? = null

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val mediaButtonActionReceiver = MediaButtonActionReceiver()
    private val notificationReceiver = NotificationReceiver()
    private var receiversRegistered = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // A single, reusable progress ticker. The previous implementation re-created
    // the Handler/Runnable on every onPlayFromUri and ticked every 10ms (~100x
    // per second) — far more often than any UI needs.
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!::mediaPlayer.isInitialized || isPlayerReleased) return
            setPlaybackState(KEEP_STATE, KEEP_SPEED)
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, LOG_TAG)
        mediaPlayer = MediaPlayer()

        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .build(),
        )

        mediaSession.setCallback(MediaSessionCallback())

        mediaPlayer.setOnCompletionListener { onTrackCompleted() }

        createNotificationChannel()
        registerReceivers()

        sessionToken = mediaSession.sessionToken
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        serviceScope.cancel()

        unregisterReceivers()

        if (::mediaPlayer.isInitialized && !isPlayerReleased) {
            mediaPlayer.release()
            isPlayerReleased = true
        }
        if (::mediaSession.isInitialized) mediaSession.release()

        super.onDestroy()
    }

    private fun onTrackCompleted() {
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED, KEEP_SPEED)
        startForeground(NOTIFICATION_ID, getNotification())

        when (mediaSession.controller.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                if (!isPlayingOnceInProgress) {
                    isPlayingOnceInProgress = true
                    if (mediaPlayer.currentPosition == mediaPlayer.duration) mediaPlayer.seekTo(0)
                    mediaSession.controller.transportControls.play()
                } else {
                    isPlayingOnceInProgress = false
                }
            }

            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                if (mediaPlayer.currentPosition == mediaPlayer.duration) mediaPlayer.seekTo(0)
                mediaSession.controller.transportControls.play()
            }
        }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
            super.onPlayFromUri(uri, extras)

            uri ?: return

            try {
                mediaPlayer.reset()
                isPlayerReleased = false
                mediaPlayer.setDataSource(this@MediaPlaybackService, uri)
                mediaPlayer.setOnPreparedListener {
                    if (!requestFocus()) return@setOnPreparedListener

                    // Promote this service to a started service so playback
                    // survives the activity unbinding. (The old code targeted
                    // the framework MediaBrowserService class by mistake.)
                    startService(
                        Intent(this@MediaPlaybackService, MediaPlaybackService::class.java),
                    )
                    mediaSession.isActive = true
                    mediaPlayer.start()

                    setPlaybackState(PlaybackStateCompat.STATE_PLAYING, KEEP_SPEED)

                    audio = AudioUtils.getMetaData(
                        this@MediaPlaybackService,
                        mediaPlayer.duration.toString(),
                        uri,
                    )
                    mediaSession.setMetadata(audio?.mediaMetadata)

                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.post(progressRunnable)

                    startForeground(NOTIFICATION_ID, getNotification())
                }
                mediaPlayer.prepareAsync()
            } catch (e: IOException) {
                // Don't crash the whole service on an unreadable file.
                Log.e(LOG_TAG, "onPlayFromUri() failed for $uri", e)
            }
        }

        override fun onPlay() {
            super.onPlay()
            if (isPlayerReleased) return

            mediaPlayer.start()
            setPlaybackState(PlaybackStateCompat.STATE_PLAYING, KEEP_SPEED)

            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.postDelayed(progressRunnable, PROGRESS_UPDATE_INTERVAL_MS)

            startForeground(NOTIFICATION_ID, getNotification())
        }

        override fun onPause() {
            super.onPause()
            if (isPlayerReleased) return

            mediaPlayer.pause()
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED, KEEP_SPEED)

            progressHandler.removeCallbacks(progressRunnable)

            @Suppress("DEPRECATION")
            stopForeground(false)
            startForeground(NOTIFICATION_ID, getNotification())
        }

        override fun onStop() {
            savePosition()

            abandonFocus()
            mediaSession.isActive = false

            progressHandler.removeCallbacks(progressRunnable)
            if (::mediaPlayer.isInitialized && !isPlayerReleased) {
                mediaPlayer.release()
                isPlayerReleased = true
            }

            stopSelf()

            @Suppress("DEPRECATION")
            stopForeground(true)

            sendBroadcast(Intent(MainActivity.KILL_APP_KEY).setPackage(packageName))
        }

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            if (!isPlayerReleased) mediaPlayer.seekTo(pos.toInt())
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            super.onSetPlaybackSpeed(speed)
            // PlaybackParams (variable speed) is only available from API 23.
            if (isPlayerReleased || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
            setPlaybackState(KEEP_STATE, speed)
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            super.onSetRepeatMode(repeatMode)
            mediaSession.setRepeatMode(repeatMode)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            super.onCustomAction(action, extras)
            action ?: return
            notificationReceiver.onReceive(
                this@MediaPlaybackService,
                Intent(action).apply { if (extras != null) putExtras(extras) },
            )
        }
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    private fun savePosition() {
        val id = audio?.id ?: return
        if (id == -1L || !mediaSession.isActive || isPlayerReleased) return

        val currentPosition = mediaPlayer.currentPosition.toLong()
        val duration = mediaPlayer.duration.toLong()

        serviceScope.launch {
            val repository = SaveItemRepository(application)
            if (currentPosition != duration) {
                repository.insertSaveItem(SaveItem(id, currentPosition))
            } else {
                repository.deleteSaveItem(SaveItem(id, currentPosition))
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceivers() {
        if (receiversRegistered) return

        val notificationFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_REPLAY)
            addAction(ACTION_CANCEL)
            addAction(ACTION_REWIND)
            addAction(ACTION_SEEK)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                RECEIVER_NOT_EXPORTED,
            )
            registerReceiver(
                mediaButtonActionReceiver,
                IntentFilter(Intent.ACTION_MEDIA_BUTTON),
                RECEIVER_NOT_EXPORTED,
            )
            registerReceiver(notificationReceiver, notificationFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            )
            registerReceiver(mediaButtonActionReceiver, IntentFilter(Intent.ACTION_MEDIA_BUTTON))
            registerReceiver(notificationReceiver, notificationFilter)
        }

        receiversRegistered = true
    }

    private fun unregisterReceivers() {
        if (!receiversRegistered) return
        // runCatching: a receiver may already be gone if the process is dying.
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        runCatching { unregisterReceiver(mediaButtonActionReceiver) }
        runCatching { unregisterReceiver(notificationReceiver) }
        receiversRegistered = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "DialogMusicPlayer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Default notification channel for DialogMusicPlayer"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getNotification(): Notification {
        val metadata = audio?.mediaMetadata

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getPendingIntent(ACTION_CANCEL))
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setColor(ContextCompat.getColor(this, R.color.notificationBGColor))
            .setSmallIcon(R.drawable.icon_fg)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.NOTIFICATION_CLICK_KEY, true),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setContentTitle(
                metadata?.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
            )
            .setContentText(metadata?.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
            .setAutoCancel(false)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_STOP,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setLargeIcon(metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_rewind,
                    "Rewind",
                    getPendingIntent(ACTION_REWIND),
                ),
            )
            .addAction(
                when (mediaSession.controller.playbackState?.state) {
                    PlaybackStateCompat.STATE_STOPPED ->
                        NotificationCompat.Action(
                            R.drawable.ic_replay,
                            "Replay",
                            getPendingIntent(ACTION_REPLAY),
                        )

                    PlaybackStateCompat.STATE_PLAYING ->
                        NotificationCompat.Action(
                            R.drawable.ic_pause,
                            "Pause",
                            getPendingIntent(ACTION_PLAY_PAUSE),
                        )

                    else ->
                        NotificationCompat.Action(
                            R.drawable.ic_play,
                            "Play",
                            getPendingIntent(ACTION_PLAY_PAUSE),
                        )
                },
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_seek,
                    "Seek",
                    getPendingIntent(ACTION_SEEK),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_clear,
                    "Close",
                    getPendingIntent(ACTION_CANCEL),
                ),
            )
            .build()
    }

    private fun getPendingIntent(action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            REQUEST_CODE,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Pushes a fresh [PlaybackStateCompat] to the session. Pass [KEEP_STATE] /
     * [KEEP_SPEED] to retain the current value for that field.
     */
    private fun setPlaybackState(playbackState: Int, playbackSpeed: Float) {
        if (!mediaSession.isActive || isPlayerReleased) return

        val state = if (playbackState == KEEP_STATE) {
            mediaSession.controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
        } else {
            playbackState
        }

        val speed = if (playbackSpeed == KEEP_SPEED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer.playbackParams.speed
            } else {
                1f
            }
        } else {
            playbackSpeed
        }

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, mediaPlayer.currentPosition.toLong(), speed)
                .setActions(SUPPORTED_ACTIONS)
                .addCustomAction(ACTION_REWIND, "Rewind", R.drawable.ic_rewind)
                .addCustomAction(ACTION_SEEK, "Seek", R.drawable.ic_seek)
                .addCustomAction(ACTION_CANCEL, "Cancel", R.drawable.ic_clear)
                .build(),
        )
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        return BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) {
        result.sendResult(null)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (isPlayerReleased) return

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            -> {
                if (wasPlayingWhenLosingAudioFocus) {
                    mediaSession.controller.transportControls.play()
                    wasPlayingWhenLosingAudioFocus = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                wasPlayingWhenLosingAudioFocus = mediaPlayer.isPlaying
                mediaSession.controller.transportControls.pause()
            }
        }
    }

    inner class BecomingNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                mediaSession.controller.transportControls.pause()
            }
        }
    }

    inner class MediaButtonActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_MEDIA_BUTTON != intent.action) return
            togglePlayPause()
        }
    }

    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controls = mediaSession.controller.transportControls
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    togglePlayPause()
                    startForeground(NOTIFICATION_ID, getNotification())
                }

                ACTION_REPLAY -> controls.play()
                ACTION_CANCEL -> controls.stop()
                ACTION_REWIND ->
                    controls.seekTo(mediaSession.controller.playbackState.position - SEEK_STEP_MS)

                ACTION_SEEK ->
                    controls.seekTo(mediaSession.controller.playbackState.position + SEEK_STEP_MS)
            }
        }
    }

    private fun togglePlayPause() {
        val controls = mediaSession.controller.transportControls
        when (mediaSession.controller.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING -> controls.pause()
            PlaybackStateCompat.STATE_PAUSED -> controls.play()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val LOG_TAG = "DMP"
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val REQUEST_CODE = 200
        private const val CHANNEL_ID = "DMPChannel"

        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val SEEK_STEP_MS = 10_000L

        // Sentinels for setPlaybackState() — "leave this field unchanged".
        private const val KEEP_STATE = -1
        private const val KEEP_SPEED = -1f

        private const val SUPPORTED_ACTIONS = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        private const val ACTION_PLAY_PAUSE = "phone.vishnu.dialogmusicplayer.playPause"
        private const val ACTION_REPLAY = "phone.vishnu.dialogmusicplayer.replay"
        private const val ACTION_CANCEL = "phone.vishnu.dialogmusicplayer.cancel"
        private const val ACTION_REWIND = "phone.vishnu.dialogmusicplayer.rewind"
        private const val ACTION_SEEK = "phone.vishnu.dialogmusicplayer.seek"
    }
}
