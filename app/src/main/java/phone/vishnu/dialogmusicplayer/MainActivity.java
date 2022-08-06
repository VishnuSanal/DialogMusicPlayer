/*
 * Copyright (C) 2021 - 2022 Vishnu Sanal. T
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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.slider.Slider;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private MediaPlayer mediaPlayer;
    private Audio audio = null;

    private Slider slider;
    private ImageView playPauseButton, repeatIV;
    private TextView fileNameTV, artistNameTV, progressTV, durationTV, playbackSpeedTV;

    private Handler updateHandler;
    private Runnable updateRunnable;

    private boolean isTimeReversed = false;
    private boolean isPlayingOnceInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setFinishOnTouchOutside(false);

        FileUtils.clearApplicationData(getApplicationContext()); // fix for an old mistake ;_;

        setScreenWidth();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) initTasks(getIntent());
            else requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        else initTasks(getIntent());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        quitApp();
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

    @Override
    public void finish() {
        super.finishAndRemoveTask();
    }

    private void initViews() {
        slider = findViewById(R.id.slider);
        playPauseButton = findViewById(R.id.playPauseButton);
        fileNameTV = findViewById(R.id.fileNameTV);
        artistNameTV = findViewById(R.id.artistNameTV);
        progressTV = findViewById(R.id.progressTV);
        durationTV = findViewById(R.id.durationTV);
        repeatIV = findViewById(R.id.repeatButton);
        repeatIV.setTag(0);
        playbackSpeedTV = findViewById(R.id.playbackSpeedButton);
        playbackSpeedTV.setTag(1.0F);
        setListeners();
    }

    private void setListeners() {
        slider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    if (mediaPlayer != null) {
                        if (fromUser) {
                            mediaPlayer.seekTo((int) value);

                            if (!mediaPlayer.isPlaying())
                                if (value != mediaPlayer.getDuration())
                                    playPauseButton.setImageResource(R.drawable.ic_play);
                                else resetMediaPlayer();
                        }
                    }
                });

        updateHandler = new Handler();
        updateRunnable =
                new Runnable() {
                    @Override
                    public void run() {

                        if (mediaPlayer != null) {

                            final int currentPosition = mediaPlayer.getCurrentPosition();

                            if (currentPosition <= slider.getValueTo())
                                slider.setValue(currentPosition);

                            progressTV.setText(getFormattedTime(currentPosition, isTimeReversed));

                            updateHandler.postDelayed(this, 10);

                            if (audio != null) {
                                if (currentPosition != mediaPlayer.getDuration())
                                    viewModel.insert(new SaveItem(audio.getId(), currentPosition));
                                else viewModel.delete(new SaveItem(audio.getId(), currentPosition));
                            }
                        }
                    }
                };

        slider.setLabelFormatter(value -> getFormattedTime((long) value, isTimeReversed));

        playPauseButton.setOnClickListener(
                v -> {
                    if (mediaPlayer != null)
                        if (mediaPlayer.isPlaying()) pauseMediaPlayer();
                        else {
                            if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration())
                                mediaPlayer.seekTo(0);

                            resumeMediaPlayer();
                        }
                });

        progressTV.setOnClickListener(v -> isTimeReversed = !isTimeReversed);

        findViewById(R.id.quitTV).setOnClickListener(v -> MainActivity.this.quitApp());

        playbackSpeedTV.setOnClickListener(
                v -> {
                    float speed = (float) playbackSpeedTV.getTag();

                    //                                float[] speeds = {0.5F, 1.0F, 1.5F, 2.0F};

                    if (speed == 0.5F) {
                        playbackSpeedTV.setText(R.string.zero_seven_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        playbackSpeedTV.setTag(0.75F);
                    } else if (speed == 0.75F) {
                        playbackSpeedTV.setText(R.string.one_x);
                        playbackSpeedTV.setTextColor(
                                getResources().getColor(R.color.textColorLight));
                        playbackSpeedTV.setTag(1F);
                    } else if (speed == 1.0F) {
                        playbackSpeedTV.setText(R.string.one_two_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        playbackSpeedTV.setTag(1.25F);
                    } else if (speed == 1.25F) {
                        playbackSpeedTV.setText(R.string.one_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        playbackSpeedTV.setTag(1.5F);
                    } else if (speed == 1.5F) {
                        playbackSpeedTV.setText(R.string.two_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        playbackSpeedTV.setTag(2.0F);
                    } else if (speed == 2.0F) {
                        playbackSpeedTV.setText(R.string.zero_five_x);
                        playbackSpeedTV.setTextColor(getResources().getColor(R.color.accentColor));
                        playbackSpeedTV.setTag(0.5F);
                    }

                    updatePlaybackSpeed();
                });

        repeatIV.setOnClickListener(
                v -> {
                    int state = (int) repeatIV.getTag();

                    // 0 -> Repeat off
                    // 1 -> Repeat once
                    // 2 -> Repeat infinitely

                    if (state == 0) {
                        repeatIV.setImageResource(R.drawable.ic_repeat_one);
                        repeatIV.setColorFilter(getResources().getColor(R.color.accentColor));
                        repeatIV.setTag(1);

                        isPlayingOnceInProgress = true;
                    } else if (state == 1) {
                        repeatIV.setImageResource(R.drawable.ic_repeat);
                        repeatIV.setColorFilter(getResources().getColor(R.color.accentColor));
                        repeatIV.setTag(2);

                        isPlayingOnceInProgress = false;
                    } else if (state == 2) {
                        repeatIV.setImageResource(R.drawable.ic_repeat);
                        repeatIV.setColorFilter(getResources().getColor(R.color.textColorLight));
                        repeatIV.setTag(0);

                        isPlayingOnceInProgress = false;
                    }
                });
    }

    private void updatePlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mediaPlayer.setPlaybackParams(
                    mediaPlayer.getPlaybackParams().setSpeed((float) playbackSpeedTV.getTag()));
    }

    private void initTasks(Intent intent) {

        initViews();

        Log.e("vishnu", "initTasks Intent#getAction: " + intent.getAction());

        if (Intent.ACTION_VIEW.equals(intent.getAction())
                || Intent.ACTION_SEND.equals(intent.getAction())) {

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
                quitApp();
                return;
            }

            viewModel = new ViewModelProvider(this).get(MainViewModel.class);

            mediaPlayer = new MediaPlayer();

            try {
                mediaPlayer.setDataSource(this, uri);
                mediaPlayer.prepare();
            } catch (IOException e) {
                Log.e("vishnu", "initTasks -> Uri: " + uri, e);
                Toast.makeText(
                                this,
                                "Oops! Something went wrong\n\n" + e + "\n\n" + "Uri: " + uri,
                                Toast.LENGTH_LONG)
                        .show();
            }

            int duration = mediaPlayer.getDuration();

            audio = AudioUtils.getMetaData(this, String.valueOf(duration), uri);

            viewModel
                    .getSaveItem(audio)
                    .observe(
                            this,
                            saveItem -> {
                                long saveTime = saveItem.getDuration();

                                if (saveTime != 0) {
                                    mediaPlayer.seekTo((int) saveTime);
                                    Toast.makeText(
                                                    this,
                                                    "Resuming playback from "
                                                            + getFormattedTime(saveTime, false),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });

            slider.setValueFrom(0);
            slider.setValueTo(duration);

            durationTV.setText(getFormattedTime(duration, isTimeReversed));

            mediaPlayer.start();
            updateHandler.postDelayed(updateRunnable, 0);
            disableScreenRotation();

            playPauseButton.setImageResource(R.drawable.ic_pause);

            mediaPlayer.setOnCompletionListener(
                    mediaPlayer -> {
                        int state = (int) repeatIV.getTag();

                        resetMediaPlayer();

                        if (state == 1) {

                            if (!isPlayingOnceInProgress) {

                                isPlayingOnceInProgress = true;

                                if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration())
                                    mediaPlayer.seekTo(0);

                                resumeMediaPlayer();

                            } else isPlayingOnceInProgress = false;

                        } else if (state == 2) {

                            if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration())
                                mediaPlayer.seekTo(0);

                            resumeMediaPlayer();
                        }
                    });

            populateMetaDataTextViews(audio);
            setTextViewScrollingBehaviour();
        }
    }

    private void setScreenWidth() {
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

    private void pauseMediaPlayer() {
        mediaPlayer.pause();
        playPauseButton.setImageResource(R.drawable.ic_play);
        enableScreenRotation();
    }

    private void resumeMediaPlayer() {
        mediaPlayer.start();
        playPauseButton.setImageResource(R.drawable.ic_pause);
        disableScreenRotation();
    }

    private void resetMediaPlayer() {
        playPauseButton.setImageResource(R.drawable.ic_replay);
        enableScreenRotation();
    }

    private void quitApp() {
        updateHandler.removeCallbacks(updateRunnable);
        mediaPlayer.release();
        finish();
    }

    private String getFormattedTime(long millis, boolean isTimeReversed) {

        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;

        String secondsStr = Long.toString(seconds);

        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;

        if (!isTimeReversed) return minutes + ":" + secs;

        return "-" + getFormattedTime(mediaPlayer.getDuration() - millis, false);
    }

    private void populateMetaDataTextViews(Audio audio) {

        try {

            Log.e("vishnu", "populateMetaDataTextViews: " + audio);

            fileNameTV.setText(audio.getName());
            artistNameTV.setText(audio.getArtist());

        } catch (Exception e) {
            Log.e("vishnu", "populateMetaDataTextViews: " + e);

            fileNameTV.setText("<Unknown Title>");
            fileNameTV.setText("<Unknown Artist>");

            e.printStackTrace();
        }
    }

    public void enableScreenRotation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public void disableScreenRotation() {
        int orientation = getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        else if (orientation == Configuration.ORIENTATION_PORTRAIT)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}
