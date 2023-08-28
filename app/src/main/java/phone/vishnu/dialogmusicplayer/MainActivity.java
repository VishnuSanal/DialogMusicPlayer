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
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
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

    private MainViewModel viewModel;

    private MediaBrowserCompat mediaBrowser;

    private Slider slider;
    private ImageView playPauseButton, repeatIV, rewindIV, seekIV;
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

                    slider.setValue((int) state.getPosition());

                    progressTV.setText(getFormattedTime(state.getPosition(), isTimeReversed));

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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setFinishOnTouchOutside(false);

        FileUtils.clearApplicationData(getApplicationContext()); // fix for an old mistake ;_;

        setScreenWidth();

        initViews();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2)
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) initTasks(getIntent());
            else requestPermissions(new String[] {Manifest.permission.READ_MEDIA_AUDIO}, 0);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
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
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (MediaControllerCompat.getMediaController(MainActivity.this) != null)
            MediaControllerCompat.getMediaController(MainActivity.this)
                    .unregisterCallback(controllerCallback);
        mediaBrowser.disconnect();
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
                return;
            }

            mediaBrowser =
                    new MediaBrowserCompat(
                            this,
                            new ComponentName(this, MediaPlaybackService.class),
                            new MediaBrowserCompat.ConnectionCallback() {
                                @Override
                                public void onConnected() {
                                    MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

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
                                    // The Service has crashed. Disable transport controls until it
                                    // automatically reconnects
                                }

                                @Override
                                public void onConnectionFailed() {
                                    // The Service has refused our connection
                                }
                            },
                            null);

            mediaBrowser.connect();
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

                    long position = (long) slider.getValue();

                    if (id != -1)
                        if (position != slider.getValueTo())
                            viewModel.insert(new SaveItem(id, position));
                        else viewModel.delete(new SaveItem(id, position));
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
        setScreenWidth();
        slider = findViewById(R.id.slider);
        playPauseButton = findViewById(R.id.playPauseButton);
        fileNameTV = findViewById(R.id.fileNameTV);
        artistNameTV = findViewById(R.id.artistNameTV);
        progressTV = findViewById(R.id.progressTV);
        durationTV = findViewById(R.id.durationTV);
        repeatIV = findViewById(R.id.repeatButton);
        rewindIV = findViewById(R.id.rewindButton);
        seekIV = findViewById(R.id.seekButton);
        playbackSpeedTV = findViewById(R.id.playbackSpeedButton);
        setTextViewScrollingBehaviour();
        setListeners();
    }

    private void setListeners() {
        findViewById(R.id.quitTV)
                .setOnClickListener(
                        v -> {
                            MediaControllerCompat.getMediaController(MainActivity.this)
                                    .getTransportControls()
                                    .stop();
                            finish();
                        });

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
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
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
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((1.25F));
                    } else if (speed == 1.25F) {
                        playbackSpeedTV.setText(R.string.one_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((1.5F));
                    } else if (speed == 1.5F) {
                        playbackSpeedTV.setText(R.string.two_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setPlaybackSpeed((2.0F));
                    } else if (speed == 2.0F) {
                        playbackSpeedTV.setText(R.string.zero_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
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
                        repeatIV.setColorFilter(getResources().getColor(R.color.accentColor));

                        MediaControllerCompat.getMediaController(MainActivity.this)
                                .getTransportControls()
                                .setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE);

                    } else if (state == PlaybackStateCompat.REPEAT_MODE_ONE) {
                        repeatIV.setImageResource(R.drawable.ic_repeat);
                        repeatIV.setColorFilter(getResources().getColor(R.color.accentColor));

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
    }

    private void setScreenWidth() {
        this.setFinishOnTouchOutside(false);

        getWindow()
                .setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
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
}
