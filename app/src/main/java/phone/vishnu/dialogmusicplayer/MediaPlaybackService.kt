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
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import java.io.IOException

class MediaPlaybackService : MediaBrowserServiceCompat(), AudioManager.OnAudioFocusChangeListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaPlayer: MediaPlayer
    private var audio: Audio? = null

    private var isPlayingOnceInProgress = false
    private var wasPlayingWhenLosingAudioFocus = false

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val mediaButtonActionReceiver = MediaButtonActionReceiver()
    private val notificationReceiver = NotificationReceiver()

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
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .build(),
        )

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {

            var updateHandler: Handler? = null
            var updateRunnable: Runnable? = null
            private var audioFocusRequest: AudioFocusRequest? = null

            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
                super.onPlayFromUri(uri, extras)

                try {
                    mediaPlayer.reset()

                    mediaPlayer.setDataSource(this@MediaPlaybackService, uri!!)
                    mediaPlayer.setOnPreparedListener { mp ->
                        if (requestFocus()) {
                            startService(
                                Intent(this@MediaPlaybackService, MediaBrowserService::class.java),
                            )
                            mediaSession.isActive = true
                            mediaPlayer.start()

                            setPlaybackState(PlaybackStateCompat.STATE_PLAYING, -1f)

                            val notificationFilter = IntentFilter()
                            notificationFilter.addAction(ACTION_PLAY_PAUSE)
                            notificationFilter.addAction(ACTION_REPLAY)
                            notificationFilter.addAction(ACTION_CANCEL)
                            notificationFilter.addAction(ACTION_REWIND)
                            notificationFilter.addAction(ACTION_SEEK)

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
                                registerReceiver(
                                    notificationReceiver,
                                    notificationFilter,
                                    RECEIVER_NOT_EXPORTED,
                                )
                            } else {
                                registerReceiver(
                                    becomingNoisyReceiver,
                                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                                )
                                registerReceiver(
                                    mediaButtonActionReceiver,
                                    IntentFilter(Intent.ACTION_MEDIA_BUTTON),
                                )
                                registerReceiver(notificationReceiver, notificationFilter)
                            }

                            audio = AudioUtils.getMetaData(
                                this@MediaPlaybackService,
                                mediaPlayer.duration.toString(),
                                uri,
                            )

                            Log.e("vishnu", "onPlayFromUri(): $audio")

                            mediaSession.setMetadata(audio!!.mediaMetadata)

                            updateHandler!!.postDelayed(updateRunnable!!, 0)

                            startForeground(NOTIFICATION_ID, getNotification())
                        }
                    }

                    mediaPlayer.prepareAsync()
                } catch (e: IOException) {
                    Log.e("vishnu", "initTasks -> Uri: $uri", e)
                    throw RuntimeException("Failed to play the requested file with Uri: $uri")
                }

                updateHandler = Handler()
                updateRunnable = object : Runnable {
                    override fun run() {
                        if (!::mediaPlayer.isInitialized) return

                        setPlaybackState(-1, -1f)

                        updateHandler!!.postDelayed(this, 10)
                    }
                }
            }

            private fun requestFocus(): Boolean {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener(this@MediaPlaybackService)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        .build()

                    audioManager.requestAudioFocus(audioFocusRequest!!) ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        this@MediaPlaybackService,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN,
                    ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
            }

            override fun onPlay() {
                super.onPlay()

                mediaPlayer.start()

                setPlaybackState(PlaybackStateCompat.STATE_PLAYING, -1f)

                updateHandler!!.postDelayed(updateRunnable!!, 10)

                startForeground(NOTIFICATION_ID, getNotification())
            }

            override fun onPause() {
                super.onPause()

                mediaPlayer.pause()

                setPlaybackState(PlaybackStateCompat.STATE_PAUSED, -1f)

                @Suppress("DEPRECATION")
                stopForeground(false)

                updateHandler!!.removeCallbacks(updateRunnable!!)

                startForeground(NOTIFICATION_ID, getNotification())
            }

            override fun onStop() {
                savePosition()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                        .abandonAudioFocusRequest(audioFocusRequest!!)
                }

                mediaSession.isActive = false

                mediaPlayer.release()
                updateHandler!!.removeCallbacks(updateRunnable!!)

                stopSelf()

                @Suppress("DEPRECATION")
                stopForeground(true)

                sendBroadcast(Intent(MainActivity.KILL_APP_KEY))
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                mediaPlayer.seekTo(pos.toInt())
            }

            override fun onSetPlaybackSpeed(speed: Float) {
                super.onSetPlaybackSpeed(speed)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
                    setPlaybackState(-1, speed)
                }
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                super.onSetRepeatMode(repeatMode)
                mediaSession.setRepeatMode(repeatMode)
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                super.onCustomAction(action, extras)
                notificationReceiver.onReceive(
                    this@MediaPlaybackService,
                    Intent(action).apply { if (extras != null) putExtras(extras) },
                )
            }
        })

        mediaPlayer.setOnCompletionListener {
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED, -1f)

            startForeground(NOTIFICATION_ID, getNotification())

            val state = mediaSession.controller.repeatMode

            if (state == PlaybackStateCompat.REPEAT_MODE_ONE) {
                if (!isPlayingOnceInProgress) {
                    isPlayingOnceInProgress = true

                    if (mediaPlayer.currentPosition == mediaPlayer.duration) {
                        mediaPlayer.seekTo(0)
                    }

                    mediaSession.controller.transportControls.play()
                } else {
                    isPlayingOnceInProgress = false
                }
            } else if (state == PlaybackStateCompat.REPEAT_MODE_ALL) {
                if (mediaPlayer.currentPosition == mediaPlayer.duration) {
                    mediaPlayer.seekTo(0)
                }

                mediaSession.controller.transportControls.play()
            }
        }

        sessionToken = mediaSession.sessionToken
    }

    private fun savePosition() {
        Log.e("vishnu", "savePosition() called")

        val id = audio?.id ?: return

        if (id == -1L || !mediaSession.isActive) return

        val currentPosition = mediaPlayer.currentPosition
        val duration = mediaPlayer.duration

        Log.e("vishnu", "savePosition: $currentPosition / $duration")

        @Suppress("DEPRECATION")
        AsyncTask.execute {
            val saveItemRepository = SaveItemRepository(application)

            if (currentPosition != duration) {
                saveItemRepository.insertSaveItem(SaveItem(id, currentPosition.toLong()))
            } else {
                saveItemRepository.deleteSaveItem(SaveItem(id, currentPosition.toLong()))
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(mediaButtonActionReceiver)
        unregisterReceiver(notificationReceiver)
        super.onDestroy()
    }

    private fun getNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "DMPChannel",
                "DialogMusicPlayer",
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationChannel.description = "Default notification channel for DialogMusicPlayer"
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(notificationChannel)
        }

        val builder = NotificationCompat.Builder(this@MediaPlaybackService, "DMPChannel")
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getPendingIntent(ACTION_CANCEL))
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setColor(ContextCompat.getColor(this@MediaPlaybackService, R.color.notificationBGColor))
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
                audio!!.mediaMetadata!!
                    .getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
            )
            .setContentText(
                audio!!.mediaMetadata!!
                    .getText(MediaMetadataCompat.METADATA_KEY_ARTIST),
            )
            .setAutoCancel(false)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_STOP,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setLargeIcon(
                audio!!.mediaMetadata!!
                    .getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_rewind,
                    "Rewind",
                    getPendingIntent(ACTION_REWIND),
                ),
            )
            .addAction(
                when (mediaSession.controller.playbackState.state) {
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

        return builder.build()
    }

    private fun getPendingIntent(action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            REQUEST_CODE,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setPlaybackState(playbackState: Int, playbackSpeed: Float) {
        if (!mediaSession.isActive) return

        val state = if (playbackState == -1) {
            mediaSession.controller.playbackState.state
        } else {
            playbackState
        }

        val speed = if (playbackSpeed == -1f) {
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
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP,
                )
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
        if (wasPlayingWhenLosingAudioFocus && focusChange == AudioManager.AUDIOFOCUS_GAIN ||
            focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        ) {
            mediaSession.controller.transportControls.play()
            wasPlayingWhenLosingAudioFocus = false
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            mediaSession.controller.transportControls.pause()
            wasPlayingWhenLosingAudioFocus = mediaPlayer.isPlaying
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

            if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                mediaSession.controller.transportControls.pause()
            } else if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PAUSED) {
                mediaSession.controller.transportControls.play()
            }
        }
    }

    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                        mediaSession.controller.transportControls.pause()
                    } else if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PAUSED) {
                        mediaSession.controller.transportControls.play()
                    }
                    startForeground(NOTIFICATION_ID, getNotification())
                }
                ACTION_REPLAY -> mediaSession.controller.transportControls.play()
                ACTION_CANCEL -> mediaSession.controller.transportControls.stop()
                ACTION_REWIND -> mediaSession.controller.transportControls.seekTo(
                    mediaSession.controller.playbackState.position - 10000,
                )
                ACTION_SEEK -> mediaSession.controller.transportControls.seekTo(
                    mediaSession.controller.playbackState.position + 10000,
                )
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val LOG_TAG = "DMP"
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val REQUEST_CODE = 200

        private const val ACTION_PLAY_PAUSE = "phone.vishnu.dialogmusicplayer.playPause"
        private const val ACTION_REPLAY = "phone.vishnu.dialogmusicplayer.replay"
        private const val ACTION_CANCEL = "phone.vishnu.dialogmusicplayer.cancel"
        private const val ACTION_REWIND = "phone.vishnu.dialogmusicplayer.rewind"
        private const val ACTION_SEEK = "phone.vishnu.dialogmusicplayer.seek"
    }
}
