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

import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.service.media.MediaBrowserService;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;
import java.io.IOException;
import java.util.List;

public class MediaPlaybackService extends MediaBrowserServiceCompat
        implements AudioManager.OnAudioFocusChangeListener {

    private static final String LOG_TAG = "DMP";
    private static final String MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id";

    private final BecomingNoisyReceiver becomingNoisyReceiver = new BecomingNoisyReceiver();
    private final MediaButtonActionReceiver mediaButtonActionReceiver =
            new MediaButtonActionReceiver();

    private MediaSessionCompat mediaSession;
    private MediaPlayer mediaPlayer;
    private Audio audio;

    private boolean isPlayingOnceInProgress = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mediaSession = new MediaSessionCompat(this, LOG_TAG);

        mediaPlayer = new MediaPlayer();

        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY
                                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                                        | PlaybackStateCompat.ACTION_PAUSE
                                        | PlaybackStateCompat.ACTION_SEEK_TO
                                        | PlaybackStateCompat.ACTION_STOP)
                        .build());

        mediaSession.setCallback(
                new MediaSessionCompat.Callback() {

                    Handler updateHandler;
                    Runnable updateRunnable;
                    private AudioFocusRequest audioFocusRequest;

                    @SuppressLint("UnspecifiedRegisterReceiverFlag")
                    @Override
                    public void onPlayFromUri(Uri uri, Bundle extras) {
                        super.onPlayFromUri(uri, extras);

                        try {
                            mediaPlayer.setDataSource(MediaPlaybackService.this, uri);
                            mediaPlayer.setOnPreparedListener(
                                    mp -> {
                                        if (requestFocus()) {

                                            startService(
                                                    new Intent(
                                                            MediaPlaybackService.this,
                                                            MediaBrowserService.class));
                                            mediaSession.setActive(true);
                                            mediaPlayer.start();

                                            setPlaybackState(PlaybackStateCompat.STATE_PLAYING, -1);

                                            registerReceiver(
                                                    becomingNoisyReceiver,
                                                    new IntentFilter(
                                                            AudioManager
                                                                    .ACTION_AUDIO_BECOMING_NOISY));
                                            registerReceiver(
                                                    mediaButtonActionReceiver,
                                                    new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

                                            audio =
                                                    AudioUtils.getMetaData(
                                                            MediaPlaybackService.this,
                                                            String.valueOf(
                                                                    mediaPlayer.getDuration()),
                                                            uri);

                                            startService(
                                                    new Intent(
                                                                    MediaPlaybackService.this,
                                                                    NotificationHelperService.class)
                                                            .putExtra(
                                                                    NotificationHelperService
                                                                            .SESSION_TOKEN_EXTRA,
                                                                    mediaSession
                                                                            .getSessionToken()));

                                            Log.e("vishnu", "onPlayFromUri(): " + audio);

                                            mediaSession.setMetadata(audio.getMediaMetadata());

                                            updateHandler.postDelayed(updateRunnable, 0);
                                        }
                                    });

                            mediaPlayer.prepareAsync();
                        } catch (IOException e) {
                            Log.e("vishnu", "initTasks -> Uri: " + uri, e);
                            throw new RuntimeException(
                                    "Failed to play the requested file with Uri: " + uri);
                        }

                        updateHandler = new Handler();
                        updateRunnable =
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        if (mediaPlayer == null) return;

                                        setPlaybackState(-1, -1);

                                        updateHandler.postDelayed(this, 10);
                                    }
                                };
                    }

                    private boolean requestFocus() {
                        AudioManager audioManager =
                                (AudioManager) getSystemService(Context.AUDIO_SERVICE);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            audioFocusRequest =
                                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                            .setOnAudioFocusChangeListener(
                                                    MediaPlaybackService.this)
                                            .setAudioAttributes(
                                                    new AudioAttributes.Builder()
                                                            .setContentType(
                                                                    AudioAttributes
                                                                            .CONTENT_TYPE_MUSIC)
                                                            .build())
                                            .build();

                            return audioManager.requestAudioFocus(audioFocusRequest)
                                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                        }
                        return audioManager.requestAudioFocus(
                                        MediaPlaybackService.this,
                                        AudioManager.STREAM_MUSIC,
                                        AudioManager.AUDIOFOCUS_GAIN)
                                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                    }

                    @Override
                    public void onPlay() {
                        super.onPlay();

                        mediaPlayer.start();

                        setPlaybackState(PlaybackStateCompat.STATE_PLAYING, -1);

                        updateHandler.postDelayed(updateRunnable, 10);

                        startService(
                                new Intent(
                                                MediaPlaybackService.this,
                                                NotificationHelperService.class)
                                        .putExtra(
                                                NotificationHelperService.SESSION_TOKEN_EXTRA,
                                                mediaSession.getSessionToken()));
                    }

                    @Override
                    public void onPause() {
                        super.onPause();

                        mediaPlayer.pause();

                        setPlaybackState(PlaybackStateCompat.STATE_PAUSED, -1);

                        stopForeground(false);

                        updateHandler.removeCallbacks(updateRunnable);

                        startService(
                                new Intent(
                                                MediaPlaybackService.this,
                                                NotificationHelperService.class)
                                        .putExtra(
                                                NotificationHelperService.SESSION_TOKEN_EXTRA,
                                                mediaSession.getSessionToken()));
                    }

                    @Override
                    public void onStop() {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                                    .abandonAudioFocusRequest(audioFocusRequest);

                        mediaSession.setActive(false);

                        mediaPlayer.release();
                        updateHandler.removeCallbacks(updateRunnable);

                        stopForeground(true);
                        stopSelf();

                        sendBroadcast(new Intent(MainActivity.KILL_APP_KEY));
                    }

                    @Override
                    public void onSeekTo(long pos) {
                        super.onSeekTo(pos);

                        mediaPlayer.seekTo((int) pos);
                    }

                    @Override
                    public void onSetPlaybackSpeed(float speed) {
                        super.onSetPlaybackSpeed(speed);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mediaPlayer.setPlaybackParams(
                                    mediaPlayer.getPlaybackParams().setSpeed(speed));

                            setPlaybackState(-1, speed);
                        }
                    }

                    @Override
                    public void onSetRepeatMode(int repeatMode) {
                        super.onSetRepeatMode(repeatMode);

                        mediaSession.setRepeatMode(repeatMode);
                    }
                });

        mediaPlayer.setOnCompletionListener(
                mp -> {
                    setPlaybackState(PlaybackStateCompat.STATE_STOPPED, -1);

                    startService(
                            new Intent(MediaPlaybackService.this, NotificationHelperService.class)
                                    .putExtra(
                                            NotificationHelperService.SESSION_TOKEN_EXTRA,
                                            mediaSession.getSessionToken()));

                    int state = mediaSession.getController().getRepeatMode();

                    if (state == PlaybackStateCompat.REPEAT_MODE_ONE) {

                        if (!isPlayingOnceInProgress) {

                            isPlayingOnceInProgress = true;

                            if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration())
                                mediaPlayer.seekTo(0);

                            mediaSession.getController().getTransportControls().play();

                        } else isPlayingOnceInProgress = false;

                    } else if (state == PlaybackStateCompat.REPEAT_MODE_ALL) {

                        if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration())
                            mediaPlayer.seekTo(0);

                        mediaSession.getController().getTransportControls().play();
                    }
                });

        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(mediaButtonActionReceiver);
        super.onDestroy();
    }

    /**
     * @param playbackState current playback state - pass -1 if unchanged, to default to the current
     *     value
     * @param playbackSpeed current playback speed - pass -1 if unchanged, to default to the current
     *     value
     */
    private void setPlaybackState(int playbackState, float playbackSpeed) {

        if (playbackState == -1)
            playbackState = mediaSession.getController().getPlaybackState().getState();

        if (playbackSpeed == -1)
            playbackSpeed =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? mediaPlayer.getPlaybackParams().getSpeed()
                            : 1F;

        mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                        .setState(playbackState, mediaPlayer.getCurrentPosition(), playbackSpeed)
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY
                                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                                        | PlaybackStateCompat.ACTION_PAUSE
                                        | PlaybackStateCompat.ACTION_SEEK_TO
                                        | PlaybackStateCompat.ACTION_STOP)
                        .build());
    }

    @Override
    public BrowserRoot onGetRoot(
            @NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(
            @NonNull final String parentMediaId,
            final Result<List<MediaBrowserCompat.MediaItem>> result) {
        //        if (TextUtils.equals(MY_EMPTY_MEDIA_ROOT_ID, parentMediaId))
        result.sendResult(null);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN)
            mediaSession.getController().getTransportControls().play();
        else if (focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            mediaSession.getController().getTransportControls().play();
        else if (focusChange == AudioManager.AUDIOFOCUS_LOSS)
            mediaSession.getController().getTransportControls().pause();
        else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT)
            mediaSession.getController().getTransportControls().pause();
    }

    class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                mediaSession.getController().getTransportControls().pause();
        }
    }

    class MediaButtonActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;

            if (mediaSession.getController().getPlaybackState().getState()
                    == PlaybackStateCompat.STATE_PLAYING)
                mediaSession.getController().getTransportControls().pause();
            else if (mediaSession.getController().getPlaybackState().getState()
                    == PlaybackStateCompat.STATE_PAUSED)
                mediaSession.getController().getTransportControls().play();
        }
    }
}
