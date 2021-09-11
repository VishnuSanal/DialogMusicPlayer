package phone.vishnu.musicplayer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setFinishOnTouchOutside(false);

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (activityManager != null) {
            List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
            if (appTasks != null && appTasks.size() > 0)
                appTasks.get(0).setExcludeFromRecents(true);
        }

        seekBar = findViewById(R.id.seekBar);
        imageView = findViewById(R.id.imageView);

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

            imageView.setImageResource(R.drawable.ic_pause);

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
    }

    private void pauseMediaPlayer() {
        mediaPlayer.pause();
        imageView.setImageResource(R.drawable.ic_play);
    }

    private void resumeMediaPlayer() {
        mediaPlayer.start();
        imageView.setImageResource(R.drawable.ic_pause);
    }

    private String getTime(long millis) {

        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;

        String secondsStr = Long.toString(seconds);

        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;

        return minutes + ":" + secs;

    }

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
}