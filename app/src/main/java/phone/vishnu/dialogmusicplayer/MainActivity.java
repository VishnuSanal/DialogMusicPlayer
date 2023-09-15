/*
 * Copyright (C) 2021 - 2023 Vishnu Sanal. T
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

package phone.vishnu.dialogmusicplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.slider.Slider;

public class MainActivity extends AppCompatActivity {

    public static final String KILL_APP_KEY = "phone.vishnu.dialogmusicplayer.kill";
    public static final String NOTIFICATION_CLICK_KEY =
            "phone.vishnu.dialogmusicplayer.notificationClick";

    private MainViewModel viewModel;

    private MediaBrowserCompat mediaBrowser;

    private Slider slider;
    private ImageView playPauseButton, repeatIV, rewindIV, seekIV, albumArtIV;
    private TextView fileNameTV, artistNameTV, progressTV, durationTV, playbackSpeedTV;

    private boolean isTimeReversed = false;

    private int totalDuration = 0;

    private long id = -1;

    private final MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    super.onMetadataChanged(metadata);

                    try {
                        id =
                                Long.parseLong(
                                        metadata.getString(
                                                MediaMetadataCompat.METADATA_KEY_MEDIA_ID));
                    } catch (NumberFormatException e) {
                        id = -1;
                        e.printStackTrace();
                    }

                    fileNameTV.setText(
                            metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE));
                    artistNameTV.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));
                    albumArtIV.setImageBitmap(
                            metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART));

                    totalDuration =
                            (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

                    durationTV.setText(getFormattedTime(totalDuration, isTimeReversed));

                    slider.setValueTo(totalDuration);

                    if (id != -1)
                        viewModel
                                .getSaveItem(id)
                                .observe(
                                        MainActivity.this,
                                        saveItem -> {
                                            long saveTime = saveItem.getDuration();

                                            if (saveTime != 0) {

                                                MediaControllerCompat.getMediaController(
                                                                MainActivity.this)
                                                        .getTransportControls()
                                                        .seekTo(saveTime);

                                                Toast.makeText(
                                                                MainActivity.this,
                                                                "Resuming playback from "
                                                                        + getFormattedTime(
                                                                                saveTime, false),
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                            }
                                        });
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);

                    long position = state.getPosition();

                    if (position <= slider.getValueTo()) slider.setValue((int) position);

                    progressTV.setText(getFormattedTime(position, isTimeReversed));

                    if (state.getState() == PlaybackStateCompat.STATE_PLAYING)
                        playPauseButton.setImageResource(R.drawable.ic_pause);
                    else if (state.getState() == PlaybackStateCompat.STATE_PAUSED)
                        playPauseButton.setImageResource(R.drawable.ic_play);
                    else if (state.getState() == PlaybackStateCompat.STATE_STOPPED)
                        playPauseButton.setImageResource(R.drawable.ic_replay);
                }

                @Override
                public void onSessionDestroyed() {
                    super.onSessionDestroyed();
                    mediaBrowser.disconnect();
                }
            };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileUtils.clearApplicationData(getApplicationContext()); // fix for an old mistake ;_;

        initViews();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        registerReceiver(killReceiver, new IntentFilter(KILL_APP_KEY));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) initTasks(getIntent());
            else
                requestPermissions(
                        new String[] {
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS
                        },
                        0);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) initTasks(getIntent());
            else requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        else initTasks(getIntent());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

        initScreen();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (MediaControllerCompat.getMediaController(MainActivity.this) != null)
            MediaControllerCompat.getMediaController(MainActivity.this)
                    .registerCallback(controllerCallback);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (MediaControllerCompat.getMediaController(MainActivity.this) != null)
            MediaControllerCompat.getMediaController(MainActivity.this)
                    .unregisterCallback(controllerCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(killReceiver);

        if (mediaBrowser != null) mediaBrowser.disconnect();

        // hack!
        // can't fix notification from getting destroyed on app exit even with the music playing :(
        if (MediaControllerCompat.getMediaController(this) != null)
            MediaControllerCompat.getMediaController(this).getTransportControls().stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        initTasks(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) initTasks(getIntent());
            else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    if (shouldShowRequestPermissionRationale(
                            Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(
                                        this,
                                        "Storage permission denied\nPlease grant necessary permissions",
                                        Toast.LENGTH_LONG)
                                .show();
                        requestPermissions(
                                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    } else {
                        Toast.makeText(
                                        this,
                                        "Storage permission denied\nPlease grant permission from settings",
                                        Toast.LENGTH_LONG)
                                .show();
                    }
            }
        }
    }

    private void initTasks(Intent intent) {

        Log.e("vishnu", "initTasks Intent#getAction: " + intent.getAction());

        if (Intent.ACTION_VIEW.equals(intent.getAction())
                || Intent.ACTION_SEND.equals(intent.getAction())) {

            //noinspection DataFlowIssue
            Uri uri =
                    Intent.ACTION_VIEW.equals(intent.getAction())
                            ? intent.getData()
                            : (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);

            Log.e("vishnu", "initTasks:" + uri);

            if (uri == null) {
                Toast.makeText(
                                this,
                                "Oops! Something went wrong\n\n" + intent.getAction(),
                                Toast.LENGTH_LONG)
                        .show();
                finish();
                return;
            }

            if (mediaBrowser == null) {
                mediaBrowser =
                        new MediaBrowserCompat(
                                this,
                                new ComponentName(this, MediaPlaybackService.class),
                                new MediaBrowserCompat.ConnectionCallback() {
                                    @Override
                                    public void onConnected() {
                                        MediaSessionCompat.Token token =
                                                mediaBrowser.getSessionToken();

                                        MediaControllerCompat mediaController =
                                                new MediaControllerCompat(MainActivity.this, token);

                                        MediaControllerCompat.setMediaController(
                                                MainActivity.this, mediaController);

                                        buildTransportControls();

                                        MediaControllerCompat.getMediaController(MainActivity.this)
                                                .getTransportControls()
                                                .playFromUri(uri, null);
                                    }

                                    @Override
                                    public void onConnectionSuspended() {
                                        // The Service has crashed. Disable transport controls until
                                        // it
                                        // automatically reconnects
                                    }

                                    @Override
                                    public void onConnectionFailed() {
                                        // The Service has refused our connection
                                    }
                                },
                                null);
                mediaBrowser.connect();
            } else {
                MediaControllerCompat.getMediaController(MainActivity.this)
                        .getTransportControls()
                        .playFromUri(uri, null);

                float playbackSpeed =
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getPlaybackState()
                                .getPlaybackSpeed();

                if (playbackSpeed != 0)
                    MediaControllerCompat.getMediaController(MainActivity.this)
                            .getTransportControls()
                            .setPlaybackSpeed(playbackSpeed);

                MediaControllerCompat.getMediaController(MainActivity.this)
                        .getTransportControls()
                        .setRepeatMode(
                                MediaControllerCompat.getMediaController(MainActivity.this)
                                        .getRepeatMode());
            }

        } else if (!intent.hasExtra(NOTIFICATION_CLICK_KEY)) {
            Toast.makeText(
                            this,
                            "Oops! Something went wrong\n\n" + intent.getAction(),
                            Toast.LENGTH_LONG)
                    .show();
            finish();
        }
    }

    void buildTransportControls() {
        playPauseButton.setOnClickListener(
                v -> {
                    int playBackState =
                            MediaControllerCompat.getMediaController(MainActivity.this)
                                    .getPlaybackState()
                                    .getState();

                    if (playBackState == PlaybackStateCompat.STATE_PLAYING) {
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .pause();
                        playPauseButton.setImageResource(R.drawable.ic_play);
                    } else if (playBackState == PlaybackStateCompat.STATE_PAUSED) {
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .play();
                        playPauseButton.setImageResource(R.drawable.ic_pause);
                    } else if (playBackState == PlaybackStateCompat.STATE_STOPPED) {
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .seekTo(0);

                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .play();
                        playPauseButton.setImageResource(R.drawable.ic_pause);
                    }
                });

        slider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    if (fromUser)
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .seekTo((int) value);
                });

        rewindIV.setOnClickListener(
                v ->
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .seekTo(
                                        MediaControllerCompat.getMediaController(MainActivity.this)
                                                        .getPlaybackState()
                                                        .getPosition()
                                                - 10000));

        seekIV.setOnClickListener(
                v ->
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .seekTo(
                                        MediaControllerCompat.getMediaController(MainActivity.this)
                                                        .getPlaybackState()
                                                        .getPosition()
                                                + 10000));

        MediaControllerCompat.getMediaController(MainActivity.this)
                .registerCallback(controllerCallback);
    }

    private void initViews() {
        slider = findViewById(R.id.slider);
        playPauseButton = findViewById(R.id.playPauseButton);
        fileNameTV = findViewById(R.id.fileNameTV);
        artistNameTV = findViewById(R.id.artistNameTV);
        progressTV = findViewById(R.id.progressTV);
        durationTV = findViewById(R.id.durationTV);
        repeatIV = findViewById(R.id.repeatButton);
        rewindIV = findViewById(R.id.rewindButton);
        seekIV = findViewById(R.id.seekButton);
        albumArtIV = findViewById(R.id.albumArtIV);
        playbackSpeedTV = findViewById(R.id.playbackSpeedButton);
        initColors();
        setTextViewScrollingBehaviour();
        setListeners();
    }

    private void initColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.e("vishnu", "isDynamicColorAvailable()");

            int colorAccent = ColorUtils.getAccentColor(this);

            int colorAccentLight = ColorUtils.getAccentColorLight(this);

            playPauseButton.setColorFilter(colorAccent);

            rewindIV.setColorFilter(colorAccentLight);
            seekIV.setColorFilter(colorAccentLight);

            slider.setThumbStrokeColor(ColorStateList.valueOf(colorAccent));
            slider.setTrackActiveTintList(ColorStateList.valueOf(colorAccent));

            slider.setHaloTintList(ColorStateList.valueOf(colorAccentLight));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        progressTV.setOnClickListener(v -> isTimeReversed = !isTimeReversed);

        slider.setLabelFormatter(value -> getFormattedTime((long) value, isTimeReversed));

        playbackSpeedTV.setOnClickListener(
                v -> {
                    float speed =
                            MediaControllerCompat.getMediaController(MainActivity.this)
                                    .getPlaybackState()
                                    .getPlaybackSpeed();

                    // float[] speeds = {0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 2.0F};

                    if (speed == 0.5F) {
                        playbackSpeedTV.setText(R.string.zero_seven_five_x);
                        playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((0.75F));
                    } else if (speed == 0.75F) {
                        playbackSpeedTV.setText(R.string.one_x);
                        playbackSpeedTV.setTextColor(
                                getResources().getColor(R.color.textColorLight));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((1F));
                    } else if (speed == 1.0F) {
                        playbackSpeedTV.setText(R.string.one_two_five_x);
                        playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((1.25F));
                    } else if (speed == 1.25F) {
                        playbackSpeedTV.setText(R.string.one_five_x);
                        playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((1.5F));
                    } else if (speed == 1.5F) {
                        playbackSpeedTV.setText(R.string.two_x);
                        playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((2.0F));
                    } else if (speed == 2.0F) {
                        playbackSpeedTV.setText(R.string.zero_five_x);
                        playbackSpeedTV.setTextColor(ColorUtils.getAccentColor(this));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((0.5F));
                    }
                });

        repeatIV.setOnClickListener(
                v -> {
                    int state =
                            MediaControllerCompat.getMediaController(MainActivity.this)
                                    .getRepeatMode();

                    if (state == PlaybackStateCompat.REPEAT_MODE_NONE) {
                        repeatIV.setImageResource(R.drawable.ic_repeat_one);
                        repeatIV.setColorFilter(ColorUtils.getAccentColor(this));

                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE);

                    } else if (state == PlaybackStateCompat.REPEAT_MODE_ONE) {
                        repeatIV.setImageResource(R.drawable.ic_repeat);
                        repeatIV.setColorFilter(ColorUtils.getAccentColor(this));

                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);

                    } else if (state == PlaybackStateCompat.REPEAT_MODE_ALL) {
                        repeatIV.setImageResource(R.drawable.ic_repeat);
                        repeatIV.setColorFilter(getResources().getColor(R.color.textColorLight));

                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
                    }
                });

        findViewById(R.id.parentRelativeLayout)
                .setOnTouchListener(
                        (v, event) -> {
                            if (event.getY() < albumArtIV.getY()
                                    || (event.getY()
                                                    < findViewById(R.id.childConstraintLayout)
                                                            .getY()
                                            && (event.getX() < albumArtIV.getX()
                                                    || event.getX()
                                                            > albumArtIV.getX()
                                                                    + albumArtIV.getWidth()))) {
                                moveTaskToBack(false);
                                return true;
                            }

                            return false;
                        });
    }

    private void initScreen() {
        this.setFinishOnTouchOutside(false);

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        getWindow()
                .setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void setTextViewScrollingBehaviour() {

        fileNameTV.setMovementMethod(new ScrollingMovementMethod());
        artistNameTV.setMovementMethod(new ScrollingMovementMethod());

        fileNameTV.setSingleLine(true);
        fileNameTV.setHorizontallyScrolling(true);
        fileNameTV.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        fileNameTV.setMarqueeRepeatLimit(-1);
        fileNameTV.setSelected(true);
        fileNameTV.setPadding(10, 0, 10, 0);

        artistNameTV.setSingleLine(true);
        artistNameTV.setHorizontallyScrolling(true);
        artistNameTV.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        artistNameTV.setMarqueeRepeatLimit(-1);
        artistNameTV.setSelected(true);
        artistNameTV.setPadding(10, 0, 10, 0);
    }

    private String getFormattedTime(long millis, boolean isTimeReversed) {

        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;

        String secondsStr = Long.toString(seconds);

        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;

        if (!isTimeReversed) return minutes + ":" + secs;

        return "-" + getFormattedTime(totalDuration - millis, false);
    }

    private final BroadcastReceiver killReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (KILL_APP_KEY.equals(intent.getAction())) finish();
                }
            };
}
