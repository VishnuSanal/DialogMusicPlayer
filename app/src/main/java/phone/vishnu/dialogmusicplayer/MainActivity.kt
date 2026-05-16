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
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider

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
    private var id = -1L

    private val controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            metadata ?: return

            try {
                id = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong()
            } catch (e: NumberFormatException) {
                id = -1
                e.printStackTrace()
            }

            fileNameTV.text = metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            artistNameTV.text = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST)
            albumArtIV.setImageBitmap(
                metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART),
            )

            totalDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()

            durationTV.text = getFormattedTime(totalDuration.toLong(), isTimeReversed)

            if (totalDuration > 0) slider.valueTo = totalDuration.toFloat()

            if (id != -1L) {
                viewModel.getSaveItem(id).observe(this@MainActivity) { saveItem ->
                    val saveTime = saveItem.duration

                    if (saveTime != 0L) {
                        MediaControllerCompat.getMediaController(this@MainActivity)
                            .transportControls
                            .seekTo(saveTime)

                        Toast.makeText(
                            this@MainActivity,
                            "Resuming playback from ${getFormattedTime(saveTime, false)}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
            state ?: return

            val position = state.position

            if (position >= slider.valueFrom && position <= slider.valueTo) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                initTasks(intent)
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                    0,
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                initTasks(intent)
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            }
        } else {
            initTasks(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        initScreen()

        volumeControlStream = AudioManager.STREAM_MUSIC

        MediaControllerCompat.getMediaController(this@MainActivity)
            ?.registerCallback(controllerCallback)
    }

    override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this@MainActivity)
            ?.unregisterCallback(controllerCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(killReceiver)

        mediaBrowser?.disconnect()

        // hack!
        // can't fix notification from getting destroyed on app exit even with the music playing :(
        MediaControllerCompat.getMediaController(this)
            ?.transportControls?.stop()
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

        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initTasks(intent)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(
                            this,
                            "Storage permission denied\nPlease grant necessary permissions",
                            Toast.LENGTH_LONG,
                        ).show()
                        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
                    } else {
                        Toast.makeText(
                            this,
                            "Storage permission denied\nPlease grant permission from settings",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finishAndRemoveTask()
    }

    private fun initTasks(intent: Intent) {
        Log.e("vishnu", "initTasks Intent#getAction: ${intent.action}")

        if (Intent.ACTION_VIEW == intent.action || Intent.ACTION_SEND == intent.action) {
            @Suppress("DEPRECATION")
            val uri: Uri? = if (Intent.ACTION_VIEW == intent.action) {
                intent.data
            } else {
                intent.extras?.get(Intent.EXTRA_STREAM) as? Uri
            }

            Log.e("vishnu", "initTasks:$uri")

            if (uri == null) {
                Toast.makeText(
                    this,
                    "Oops! Something went wrong\n\n${intent.action}",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return
            }

            if (mediaBrowser == null) {
                mediaBrowser = MediaBrowserCompat(
                    this,
                    ComponentName(this, MediaPlaybackService::class.java),
                    object : MediaBrowserCompat.ConnectionCallback() {
                        override fun onConnected() {
                            val token = mediaBrowser!!.sessionToken

                            val mediaController = MediaControllerCompat(this@MainActivity, token)

                            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                            buildTransportControls()

                            MediaControllerCompat.getMediaController(this@MainActivity)
                                .transportControls
                                .playFromUri(uri, null)
                        }

                        override fun onConnectionSuspended() {
                            // The Service has crashed. Disable transport controls until it
                            // automatically reconnects
                        }

                        override fun onConnectionFailed() {
                            // The Service has refused our connection
                        }
                    },
                    null,
                )
                mediaBrowser!!.connect()
            } else {
                MediaControllerCompat.getMediaController(this@MainActivity)
                    .transportControls
                    .playFromUri(uri, null)

                val playbackSpeed = MediaControllerCompat.getMediaController(this@MainActivity)
                    .playbackState
                    .playbackSpeed

                if (playbackSpeed != 0f) {
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls
                        .setPlaybackSpeed(playbackSpeed)
                }

                MediaControllerCompat.getMediaController(this@MainActivity)
                    .transportControls
                    .setRepeatMode(
                        MediaControllerCompat.getMediaController(this@MainActivity).repeatMode,
                    )
            }
        } else if (!intent.hasExtra(NOTIFICATION_CLICK_KEY)) {
            Toast.makeText(
                this,
                "Oops! Something went wrong\n\n${intent.action}",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    fun buildTransportControls() {
        playPauseButton.setOnClickListener {
            val playBackState = MediaControllerCompat.getMediaController(this@MainActivity)
                .playbackState
                .state

            when (playBackState) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.pause()
                    playPauseButton.setImageResource(R.drawable.ic_play)
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.play()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                }
                PlaybackStateCompat.STATE_STOPPED -> {
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.seekTo(0)
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.play()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                MediaControllerCompat.getMediaController(this@MainActivity)
                    .transportControls
                    .seekTo(value.toInt().toLong())
            }
        }

        rewindIV.setOnClickListener {
            MediaControllerCompat.getMediaController(this@MainActivity)
                .transportControls
                .seekTo(
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .playbackState.position - 10000,
                )
        }

        seekIV.setOnClickListener {
            MediaControllerCompat.getMediaController(this@MainActivity)
                .transportControls
                .seekTo(
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .playbackState.position + 10000,
                )
        }

        MediaControllerCompat.getMediaController(this@MainActivity)
            .registerCallback(controllerCallback)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.e("vishnu", "isDynamicColorAvailable()")

            val colorAccent = ColorUtils.getAccentColor(this)
            val colorAccentLight = ColorUtils.getAccentColorLight(this)

            playPauseButton.setColorFilter(colorAccent)

            rewindIV.setColorFilter(colorAccentLight)
            seekIV.setColorFilter(colorAccentLight)

            slider.thumbStrokeColor = ColorStateList.valueOf(colorAccent)
            slider.trackActiveTintList = ColorStateList.valueOf(colorAccent)
            slider.haloTintList = ColorStateList.valueOf(colorAccentLight)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setListeners() {
        progressTV.setOnClickListener { isTimeReversed = !isTimeReversed }

        slider.setLabelFormatter { value -> getFormattedTime(value.toLong(), isTimeReversed) }

        playbackSpeedTV.setOnClickListener {
            val speed = MediaControllerCompat.getMediaController(this@MainActivity)
                .playbackState
                .playbackSpeed

            when (speed) {
                0.5f -> {
                    playbackSpeedTV.setText(R.string.zero_seven_five_x)
                    playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(0.75f)
                }
                0.75f -> {
                    playbackSpeedTV.setText(R.string.one_x)
                    @Suppress("DEPRECATION")
                    playbackSpeedTV.setTextColor(resources.getColor(R.color.textColorLight))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(1f)
                }
                1.0f -> {
                    playbackSpeedTV.setText(R.string.one_two_five_x)
                    playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(1.25f)
                }
                1.25f -> {
                    playbackSpeedTV.setText(R.string.one_five_x)
                    playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(1.5f)
                }
                1.5f -> {
                    playbackSpeedTV.setText(R.string.two_x)
                    playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(2.0f)
                }
                2.0f -> {
                    playbackSpeedTV.setText(R.string.zero_five_x)
                    playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls.setPlaybackSpeed(0.5f)
                }
            }
        }

        repeatIV.setOnClickListener {
            val state = MediaControllerCompat.getMediaController(this@MainActivity).repeatMode

            when (state) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> {
                    repeatIV.setImageResource(R.drawable.ic_repeat_one)
                    repeatIV.setColorFilter(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls
                        .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
                }
                PlaybackStateCompat.REPEAT_MODE_ONE -> {
                    repeatIV.setImageResource(R.drawable.ic_repeat)
                    repeatIV.setColorFilter(ColorUtils.getAccentColor(this))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls
                        .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)
                }
                PlaybackStateCompat.REPEAT_MODE_ALL -> {
                    repeatIV.setImageResource(R.drawable.ic_repeat)
                    @Suppress("DEPRECATION")
                    repeatIV.setColorFilter(resources.getColor(R.color.textColorLight))
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        .transportControls
                        .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
                }
            }
        }

        findViewById<View>(R.id.parentRelativeLayout).setOnTouchListener { _, event ->
            if (event.y < albumArtIV.y ||
                (
                    event.y < findViewById<View>(R.id.childConstraintLayout).y &&
                        (event.x < albumArtIV.x || event.x > albumArtIV.x + albumArtIV.width)
                    )
            ) {
                moveTaskToBack(false)
                true
            } else {
                false
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
        fileNameTV.movementMethod = ScrollingMovementMethod()
        artistNameTV.movementMethod = ScrollingMovementMethod()

        fileNameTV.isSingleLine = true
        fileNameTV.setHorizontallyScrolling(true)
        fileNameTV.ellipsize = TextUtils.TruncateAt.MARQUEE
        fileNameTV.marqueeRepeatLimit = -1
        fileNameTV.isSelected = true
        fileNameTV.setPadding(10, 0, 10, 0)

        artistNameTV.isSingleLine = true
        artistNameTV.setHorizontallyScrolling(true)
        artistNameTV.ellipsize = TextUtils.TruncateAt.MARQUEE
        artistNameTV.marqueeRepeatLimit = -1
        artistNameTV.isSelected = true
        artistNameTV.setPadding(10, 0, 10, 0)
    }

    private fun getFormattedTime(millis: Long, isTimeReversed: Boolean): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60

        val secondsStr = seconds.toString()
        val secs = if (secondsStr.length >= 2) secondsStr.substring(0, 2) else "0$secondsStr"

        return if (!isTimeReversed) {
            "$minutes:$secs"
        } else {
            "-${getFormattedTime(totalDuration - millis, false)}"
        }
    }

    private val killReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (KILL_APP_KEY == intent.action) finish()
        }
    }

    companion object {
        const val KILL_APP_KEY = "phone.vishnu.dialogmusicplayer.kill"
        const val NOTIFICATION_CLICK_KEY = "phone.vishnu.dialogmusicplayer.notificationClick"
    }
}
