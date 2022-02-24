/*
 * Copyright (C) 2021 - 2021 Vishnu Sanal. T
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
import android.media.MediaMetadataRetriever;
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
import com.google.android.material.slider.Slider;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;

    private Slider slider;
    private ImageView imageView;

    private TextView fileNameTV, artistNameTV;

    private Handler updateHandler;
    private Runnable updateRunnable;

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

    private void initViews() {
        slider = findViewById(R.id.slider);
        imageView = findViewById(R.id.playPauseButton);
        fileNameTV = findViewById(R.id.fileNameTV);
        artistNameTV = findViewById(R.id.artistNameTV);
        setListeners();
    }

    private void setListeners() {
        slider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    if (mediaPlayer != null) if (fromUser) mediaPlayer.seekTo((int) value);
                });

        updateHandler = new Handler();
        updateRunnable =
                new Runnable() {
                    @Override
                    public void run() {

                        if (mediaPlayer != null) {
                            slider.setValue(mediaPlayer.getCurrentPosition());

                            ((TextView) findViewById(R.id.progressTV))
                                    .setText(getFormattedTime(mediaPlayer.getCurrentPosition()));

                            updateHandler.postDelayed(this, 10);
                        }
                    }
                };

        slider.setLabelFormatter(value -> getFormattedTime((long) value));

        imageView.setOnClickListener(
                v -> {
                    if (mediaPlayer.isPlaying()) {
                        pauseMediaPlayer();
                    } else {
                        resumeMediaPlayer();
                    }
                });

        findViewById(R.id.quitTV).setOnClickListener(v -> MainActivity.this.quitApp());
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

            if (uri == null)
                Toast.makeText(
                                this,
                                "Oops! Something went wrong\n\n" + intent.getAction(),
                                Toast.LENGTH_LONG)
                        .show();

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

            slider.setValueFrom(0);
            slider.setValueTo(mediaPlayer.getDuration());

            ((TextView) findViewById(R.id.durationTV))
                    .setText(getFormattedTime(mediaPlayer.getDuration()));

            mediaPlayer.start();
            updateHandler.postDelayed(updateRunnable, 0);
            disableScreenRotation();

            imageView.setImageResource(R.drawable.ic_pause);

            populateMetaDataTextViews(uri);
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
        imageView.setImageResource(R.drawable.ic_play);
        enableScreenRotation();
    }

    private void resumeMediaPlayer() {
        mediaPlayer.start();
        imageView.setImageResource(R.drawable.ic_pause);
        disableScreenRotation();
    }

    private void quitApp() {
        updateHandler.removeCallbacks(updateRunnable);
        mediaPlayer.release();
        finish();
    }

    private String getFormattedTime(long millis) {

        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;

        String secondsStr = Long.toString(seconds);

        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;

        return minutes + ":" + secs;
    }

    private void populateMetaDataTextViews(Uri uri) {

        String title = null, artist = null;

        try {

            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

            String s = uri.getPath().replace(uri.getPath().split("/")[1], "");

            Log.e("vishnu", "populateMetaDataTextViews: " + s);

            mediaMetadataRetriever.setDataSource(s);

            title =
                    mediaMetadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist =
                    mediaMetadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_ARTIST);

        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] split = uri.getLastPathSegment().split("/");

        if (split.length == 0) split = new String[] {"<Unknown Title>"};

        fileNameTV.setText(title == null ? split[split.length - 1] : title);
        artistNameTV.setText(artist == null ? "<Unknown Artist>" : artist);
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
