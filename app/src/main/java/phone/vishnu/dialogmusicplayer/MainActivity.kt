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

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var mediaBrowser: MediaBrowserCompat? = null

    // Guards against re-seeking to the saved position every time metadata is
    // re-delivered (e.g. on configuration change / re-registering the callback).
    private var lastResumedId = -1L

    private val mediaController: MediaControllerCompat?
        get() = MediaControllerCompat.getMediaController(this)

    private val transportControls: MediaControllerCompat.TransportControls?
        get() = mediaController?.transportControls

    private val controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            metadata ?: return

            val id = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                ?.toLongOrNull() ?: -1L

            viewModel.onMetadataChanged(
                title = metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                    ?.toString().orEmpty(),
                artist = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST)
                    ?.toString().orEmpty(),
                albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART),
                durationMs = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
            )

            if (id != -1L && id != lastResumedId) {
                lastResumedId = id
                observeSavedPosition(id)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
            state ?: return
            viewModel.onPlaybackStateChanged(state.state, state.position)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            viewModel.onRepeatModeChanged(repeatMode)
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            mediaBrowser?.disconnect()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileUtils.clearApplicationData(applicationContext) // fix for an old mistake ;_;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(killReceiver, IntentFilter(KILL_APP_KEY), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(killReceiver, IntentFilter(KILL_APP_KEY))
        }

        setContent {
            DmpTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                PlayerScreen(
                    state = state,
                    onPlayPause = ::togglePlayPause,
                    onSeekTo = { transportControls?.seekTo(it) },
                    onRewind = { seekBy(-SEEK_STEP_MS) },
                    onForward = { seekBy(SEEK_STEP_MS) },
                    onRepeat = ::cycleRepeatMode,
                    onBackgroundTap = { moveTaskToBack(false) },
                )
            }
        }

        // Playback only needs the audio-read permission. POST_NOTIFICATIONS is
        // optional (a nicer notification) and must NOT gate playback — so we
        // start as soon as the essential permission is available and request
        // anything still missing separately.
        if (hasEssentialPermission()) initTasks(intent)
        requestMissingPermissions()
    }

    /** The one permission without which the app genuinely cannot play a file. */
    private fun essentialPermission(): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                Manifest.permission.READ_MEDIA_AUDIO

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                Manifest.permission.READ_EXTERNAL_STORAGE

            else -> null
        }
    }

    private fun hasEssentialPermission(): Boolean {
        val permission = essentialPermission() ?: return true
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

            else -> emptyList()
        }
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()

        initScreen()

        volumeControlStream = AudioManager.STREAM_MUSIC

        mediaController?.registerCallback(controllerCallback)
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(killReceiver)

        mediaBrowser?.disconnect()

        // hack!
        // can't fix notification from getting destroyed on app exit even with the music playing :(
        transportControls?.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initTasks(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        // Only the essential (audio) permission decides whether we can play —
        // a denied POST_NOTIFICATIONS is fine and must not block playback.
        if (hasEssentialPermission()) {
            // Start playback if onCreate couldn't (permission was just granted).
            if (mediaBrowser == null) initTasks(intent)
            return
        }

        val essential = essentialPermission() ?: return
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, essential)) {
            Toast.makeText(
                this,
                "Audio permission denied\nPlease grant it so files can be played",
                Toast.LENGTH_LONG,
            ).show()
            requestMissingPermissions()
        } else {
            Toast.makeText(
                this,
                "Audio permission denied\nPlease grant the permission from settings",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun finish() {
        super.finishAndRemoveTask()
    }

    // ---- Transport actions (invoked by the Composable) -------------------------

    private fun togglePlayPause() {
        when (mediaController?.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING -> transportControls?.pause()
            PlaybackStateCompat.STATE_PAUSED -> transportControls?.play()
            PlaybackStateCompat.STATE_STOPPED -> {
                transportControls?.seekTo(0)
                transportControls?.play()
            }
        }
    }

    private fun seekBy(deltaMs: Long) {
        val position = mediaController?.playbackState?.position ?: return
        transportControls?.seekTo(position + deltaMs)
    }

    private fun cycleRepeatMode() {
        val next = when (mediaController?.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            PlaybackStateCompat.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ALL
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
        transportControls?.setRepeatMode(next)
        viewModel.onRepeatModeChanged(next)
    }

    // ---- Media browser plumbing ------------------------------------------------

    private fun initTasks(intent: Intent) {
        if (Intent.ACTION_VIEW != intent.action && Intent.ACTION_SEND != intent.action) {
            if (!intent.hasExtra(NOTIFICATION_CLICK_KEY)) {
                showFatalError(intent.action)
            }
            return
        }

        val uri: Uri? = if (Intent.ACTION_VIEW == intent.action) {
            intent.data
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        }

        if (uri == null) {
            showFatalError(intent.action)
            return
        }

        if (mediaBrowser == null) {
            connectAndPlay(uri)
        } else {
            playUri(uri)
        }
    }

    private fun showFatalError(action: String?) {
        Toast.makeText(this, "Oops! Something went wrong\n\n$action", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun connectAndPlay(uri: Uri) {
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val browser = mediaBrowser ?: return
                    val controller = MediaControllerCompat(this@MainActivity, browser.sessionToken)
                    MediaControllerCompat.setMediaController(this@MainActivity, controller)

                    controller.registerCallback(controllerCallback)
                    viewModel.onRepeatModeChanged(controller.repeatMode)

                    controller.transportControls.playFromUri(uri, null)
                }

                override fun onConnectionSuspended() {
                    // The service has crashed; transport controls become no-ops
                    // until MediaBrowser reconnects automatically.
                }

                override fun onConnectionFailed() {
                    // The service refused the connection.
                }
            },
            null,
        ).also { it.connect() }
    }

    private fun playUri(uri: Uri) {
        val controller = mediaController ?: return
        controller.transportControls.playFromUri(uri, null)

        val playbackSpeed = controller.playbackState?.playbackSpeed ?: 0f
        if (playbackSpeed != 0f) {
            controller.transportControls.setPlaybackSpeed(playbackSpeed)
        }

        controller.transportControls.setRepeatMode(controller.repeatMode)
    }

    private fun observeSavedPosition(id: Long) {
        viewModel.getSaveItem(id).observe(this) { saveItem ->
            val saveTime = saveItem.duration
            if (saveTime != 0L) {
                transportControls?.seekTo(saveTime)
                Toast.makeText(
                    this,
                    "Resuming playback from ${formatTime(saveTime)}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun initScreen() {
        setFinishOnTouchOutside(false)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private val killReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (KILL_APP_KEY == intent.action) finish()
        }
    }

    companion object {
        const val KILL_APP_KEY = "phone.vishnu.dialogmusicplayer.kill"
        const val NOTIFICATION_CLICK_KEY = "phone.vishnu.dialogmusicplayer.notificationClick"

        private const val PERMISSION_REQUEST_CODE = 0
        private const val SEEK_STEP_MS = 10_000L
    }
}
