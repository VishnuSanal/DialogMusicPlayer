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
import android.content.res.ColorStateList
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    private var mediaBrowser: MediaBrowserCompat? = null

    private lateinit var slider: Slider
    private lateinit var playPauseButton: ImageView
    private lateinit var repeatIV: ImageView
    private lateinit var rewindIV: ImageView
    private lateinit var seekIV: ImageView
    private lateinit var albumArtIV: ImageView
    private lateinit var fileNameTV: TextView
    private lateinit var artistNameTV: TextView
    private lateinit var progressTV: TextView
    private lateinit var durationTV: TextView
    private lateinit var playbackSpeedTV: TextView

    private var isTimeReversed = false
    private var totalDuration = 0
    private var trackId = -1L

    // Guards against re-seeking to the saved position every time metadata is
    // re-delivered (e.g. on configuration change / re-registering the callback).
    private var lastResumedId = -1L

    /** Convenience accessors so we stop repeating the verbose static lookup. */
    private val mediaController: MediaControllerCompat?
        get() = MediaControllerCompat.getMediaController(this)

    private val transportControls: MediaControllerCompat.TransportControls?
        get() = mediaController?.transportControls

    private val controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            metadata ?: return

            trackId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                ?.toLongOrNull() ?: -1L

            fileNameTV.text = metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            artistNameTV.text = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST)
            albumArtIV.setImageBitmap(
                metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART),
            )

            totalDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
            durationTV.text = getFormattedTime(totalDuration.toLong(), isTimeReversed)

            if (totalDuration > 0) slider.valueTo = totalDuration.toFloat()

            if (trackId != -1L && trackId != lastResumedId) {
                lastResumedId = trackId
                observeSavedPosition(trackId)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
            state ?: return

            val position = state.position
            if (position in slider.valueFrom.toLong()..slider.valueTo.toLong()) {
                slider.value = position.toFloat()
            }

            progressTV.text = getFormattedTime(position, isTimeReversed)

            when (state.state) {
                PlaybackStateCompat.STATE_PLAYING ->
                    playPauseButton.setImageResource(R.drawable.ic_pause)

                PlaybackStateCompat.STATE_PAUSED ->
                    playPauseButton.setImageResource(R.drawable.ic_play)

                PlaybackStateCompat.STATE_STOPPED ->
                    playPauseButton.setImageResource(R.drawable.ic_replay)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            mediaBrowser?.disconnect()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FileUtils.clearApplicationData(applicationContext) // fix for an old mistake ;_;

        initViews()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(killReceiver, IntentFilter(KILL_APP_KEY), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(killReceiver, IntentFilter(KILL_APP_KEY))
        }

        requestPermissionsThenStart()
    }

    private fun requestPermissionsThenStart() {
        val required = requiredPermissions()

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            initTasks(intent)
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
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

        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            initTasks(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(
                    this,
                    "Storage permission denied\nPlease grant necessary permissions",
                    Toast.LENGTH_LONG,
                ).show()
                requestPermissionsThenStart()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied\nPlease grant permission from settings",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun finish() {
        super.finishAndRemoveTask()
    }

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
                    val mediaController = MediaControllerCompat(this@MainActivity, browser.sessionToken)
                    MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                    buildTransportControls()

                    transportControls?.playFromUri(uri, null)
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

    private fun buildTransportControls() {
        playPauseButton.setOnClickListener {
            when (mediaController?.playbackState?.state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    transportControls?.pause()
                    playPauseButton.setImageResource(R.drawable.ic_play)
                }

                PlaybackStateCompat.STATE_PAUSED -> {
                    transportControls?.play()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                }

                PlaybackStateCompat.STATE_STOPPED -> {
                    transportControls?.seekTo(0)
                    transportControls?.play()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) transportControls?.seekTo(value.toLong())
        }

        rewindIV.setOnClickListener {
            val position = mediaController?.playbackState?.position ?: return@setOnClickListener
            transportControls?.seekTo(position - SEEK_STEP_MS)
        }

        seekIV.setOnClickListener {
            val position = mediaController?.playbackState?.position ?: return@setOnClickListener
            transportControls?.seekTo(position + SEEK_STEP_MS)
        }

        // Registered here so the very first (post-connection) callback is wired
        // up; onResume/onStop take over for subsequent foreground transitions.
        mediaController?.registerCallback(controllerCallback)
    }

    private fun observeSavedPosition(id: Long) {
        viewModel.getSaveItem(id).observe(this) { saveItem ->
            val saveTime = saveItem.duration
            if (saveTime != 0L) {
                transportControls?.seekTo(saveTime)
                Toast.makeText(
                    this,
                    "Resuming playback from ${getFormattedTime(saveTime, false)}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun initViews() {
        slider = findViewById(R.id.slider)
        playPauseButton = findViewById(R.id.playPauseButton)
        fileNameTV = findViewById(R.id.fileNameTV)
        artistNameTV = findViewById(R.id.artistNameTV)
        progressTV = findViewById(R.id.progressTV)
        durationTV = findViewById(R.id.durationTV)
        repeatIV = findViewById(R.id.repeatButton)
        rewindIV = findViewById(R.id.rewindButton)
        seekIV = findViewById(R.id.seekButton)
        albumArtIV = findViewById(R.id.albumArtIV)
        playbackSpeedTV = findViewById(R.id.playbackSpeedButton)
        initColors()
        setTextViewScrollingBehaviour()
        setListeners()
    }

    private fun initColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val colorAccent = ColorUtils.getAccentColor(this)
        val colorAccentLight = ColorUtils.getAccentColorLight(this)

        playPauseButton.setColorFilter(colorAccent)
        rewindIV.setColorFilter(colorAccentLight)
        seekIV.setColorFilter(colorAccentLight)

        slider.thumbStrokeColor = ColorStateList.valueOf(colorAccent)
        slider.trackActiveTintList = ColorStateList.valueOf(colorAccent)
        slider.haloTintList = ColorStateList.valueOf(colorAccentLight)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setListeners() {
        progressTV.setOnClickListener { isTimeReversed = !isTimeReversed }

        slider.setLabelFormatter { value -> getFormattedTime(value.toLong(), isTimeReversed) }

        playbackSpeedTV.setOnClickListener { cyclePlaybackSpeed() }

        repeatIV.setOnClickListener { cycleRepeatMode() }

        findViewById<View>(R.id.parentRelativeLayout).setOnTouchListener { _, event ->
            val childLayout = findViewById<View>(R.id.childConstraintLayout)
            val tappedOutsideContent = event.y < albumArtIV.y ||
                (
                    event.y < childLayout.y &&
                        (event.x < albumArtIV.x || event.x > albumArtIV.x + albumArtIV.width)
                    )

            if (tappedOutsideContent) {
                moveTaskToBack(false)
                true
            } else {
                false
            }
        }
    }

    private fun cyclePlaybackSpeed() {
        val speed = mediaController?.playbackState?.playbackSpeed ?: 1f

        // Cycle 0.5 -> 0.75 -> 1 -> 1.25 -> 1.5 -> 2 -> 0.5 ...
        val next = when (speed) {
            0.5f -> 0.75f
            0.75f -> 1f
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.5f
            else -> 1f
        }

        playbackSpeedTV.setText(speedLabelRes(next))
        playbackSpeedTV.setTextColor(
            if (next == 1f) {
                ContextCompat.getColor(this, R.color.textColorLight)
            } else {
                ColorUtils.getAccentColor(this)
            },
        )
        transportControls?.setPlaybackSpeed(next)
    }

    private fun speedLabelRes(speed: Float): Int = when (speed) {
        0.5f -> R.string.zero_five_x
        0.75f -> R.string.zero_seven_five_x
        1.25f -> R.string.one_two_five_x
        1.5f -> R.string.one_five_x
        2.0f -> R.string.two_x
        else -> R.string.one_x
    }

    private fun cycleRepeatMode() {
        when (mediaController?.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                repeatIV.setImageResource(R.drawable.ic_repeat_one)
                repeatIV.setColorFilter(ColorUtils.getAccentColor(this))
                transportControls?.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
            }

            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                repeatIV.setImageResource(R.drawable.ic_repeat)
                repeatIV.setColorFilter(ColorUtils.getAccentColor(this))
                transportControls?.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)
            }

            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                repeatIV.setImageResource(R.drawable.ic_repeat)
                repeatIV.setColorFilter(ContextCompat.getColor(this, R.color.textColorLight))
                transportControls?.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
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

    private fun setTextViewScrollingBehaviour() {
        for (textView in listOf(fileNameTV, artistNameTV)) {
            textView.movementMethod = ScrollingMovementMethod()
            textView.isSingleLine = true
            textView.setHorizontallyScrolling(true)
            textView.ellipsize = TextUtils.TruncateAt.MARQUEE
            textView.marqueeRepeatLimit = -1
            textView.isSelected = true
            textView.setPadding(10, 0, 10, 0)
        }
    }

    private fun getFormattedTime(millis: Long, reversed: Boolean): String {
        if (reversed) return "-" + getFormattedTime(totalDuration - millis, false)

        val totalSeconds = millis / 1000
        return String.format(
            Locale.getDefault(),
            "%d:%02d",
            totalSeconds / 60,
            totalSeconds % 60,
        )
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
