package phone.vishnu.musicplayer;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;

    private SeekBar seekBar;
    private ImageView imageView;

    private TextView fileNameTV, artistNameTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setFinishOnTouchOutside(false);

        getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (activityManager != null) {
            List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
            if (appTasks != null && appTasks.size() > 0)
                appTasks.get(0).setExcludeFromRecents(true);
        }

        seekBar = findViewById(R.id.seekBar);
        imageView = findViewById(R.id.playPauseButton);
        fileNameTV = findViewById(R.id.fileNameTV);
        artistNameTV = findViewById(R.id.artistNameTV);

        final Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {

            String path = intent.getData().getPath().replace("/storage_root/", "");

            mediaPlayer = new MediaPlayer();

            try {
                mediaPlayer.setDataSource(path);
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Oops! Something went wrong\n\n" + e.toString(), Toast.LENGTH_SHORT).show();
            }

            seekBar.setMax(mediaPlayer.getDuration());
            ((TextView) findViewById(R.id.durationTV)).setText(getTime(mediaPlayer.getDuration()));

            mediaPlayer.start();
            disableScreenRotation();

            imageView.setImageResource(R.drawable.ic_pause);

            setMetaData(path);
            setScrollingBehaviour();

            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                }
            }, 0, 1);

        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mediaPlayer != null)
                    if (fromUser)
                        mediaPlayer.seekTo(progress);
                    else
                        ((TextView) findViewById(R.id.progressTV)).setText(getTime(mediaPlayer.getCurrentPosition()));
            }
        });

        imageView.setOnClickListener(v -> {

            if (mediaPlayer.isPlaying()) {
                pauseMediaPlayer();
            } else {
                resumeMediaPlayer();
            }

        });

        findViewById(R.id.quitTV).setOnClickListener(v -> MainActivity.this.quit());
    }

    private void setScrollingBehaviour() {

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

    private void quit() {
        mediaPlayer.release();
        finish();
    }

    private String getTime(long millis) {

        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;

        String secondsStr = Long.toString(seconds);

        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;

        return minutes + ":" + secs;

    }

    private void setMetaData(String path) {

        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(path);

        String title = null, artist = null;
        try {
            title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] split = path.split("/");

        if (split.length == 0) split = new String[]{"<Unknown Title>"};

        fileNameTV.setText
                (title == null ?
                        split[split.length - 1] :
                        title);
        artistNameTV.setText(
                artist == null ?
                        "<Unknown Artist>" :
                        artist
        );

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        quit();
    }

    /*
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("position", mediaPlayer.getCurrentPosition());
        pauseMediaPlayer();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mediaPlayer.seekTo(savedInstanceState.getInt("position"));
        resumeMediaPlayer();
        super.onRestoreInstanceState(savedInstanceState);
    }
    */

    /*
    @Override
    protected void onPause() {
        super.onPause();
        pauseMediaPlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mediaPlayer.release();
        finish();
    }
    */

    @SuppressLint("SourceLockedOrientationActivity")
    public void disableScreenRotation() {
        int orientation = getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        else if (orientation == Configuration.ORIENTATION_PORTRAIT)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void enableScreenRotation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

}