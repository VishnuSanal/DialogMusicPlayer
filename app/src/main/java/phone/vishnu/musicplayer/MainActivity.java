package phone.vishnu.musicplayer;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Intent intent = getIntent();

        if (
                Intent.ACTION_VIEW.equals(intent.getAction()) &&
                        intent.getData() != null
        )
            playAudio(
                    intent.getData().getPath().replace("/storage_root/", "")
            );

    }

    public void playAudio(String path) {
        MediaPlayer mediaPlayer = new MediaPlayer();

        try {

            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Oops! Something went wrong\n\n" + e.toString(), Toast.LENGTH_SHORT).show();
        }

    }
}