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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import java.io.IOException;
import java.util.List;

public class MediaPlaybackService extends MediaBrowserServiceCompat {

    private static final int NOTIFICATION_ID = 2;

    private static final String LOG_TAG = "DMP";
    private static final String MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id";

    private static final int REQUEST_CODE = 200;

    private static final String ACTION_PLAY_PAUSE = "phone.vishnu.dialogmusicplayer.playPause";
    private static final String ACTION_CANCEL = "phone.vishnu.dialogmusicplayer.cancel";
    private static final String ACTION_REWIND = "phone.vishnu.dialogmusicplayer.rewind";
    private static final String ACTION_SEEK = "phone.vishnu.dialogmusicplayer.seek";

    private final BecomingNoisyReceiver becomingNoisyReceiver = new BecomingNoisyReceiver();
    private final MediaButtonActionReceiver mediaButtonActionReceiver =
            new MediaButtonActionReceiver();
    private final NotificationReceiver notificationReceiver = new NotificationReceiver();

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
                                        | PlaybackStateCompat.ACTION_PAUSE)
                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
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
                                        AudioManager audioManager =
                                                (AudioManager)
                                                        getSystemService(Context.AUDIO_SERVICE);

                                        boolean flag =
                                                Build.VERSION.SDK_INT < Build.VERSION_CODES.O;

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            audioFocusRequest =
                                                    new AudioFocusRequest.Builder(
                                                                    AudioManager.AUDIOFOCUS_GAIN)
                                                            .setOnAudioFocusChangeListener(
                                                                    focusChange -> {
                                                                        if (focusChange
                                                                                == AUDIOFOCUS_LOSS_TRANSIENT)
                                                                            onPause();
                                                                        else if (focusChange
                                                                                == AudioManager
                                                                                        .AUDIOFOCUS_GAIN_TRANSIENT)
                                                                            onPlay();
                                                                        else if (focusChange
                                                                                == AudioManager
                                                                                        .AUDIOFOCUS_LOSS)
                                                                            onPause();
                                                                        else if (focusChange
                                                                                == AudioManager
                                                                                        .AUDIOFOCUS_GAIN)
                                                                            onPlay();
                                                                    })
                                                            .setAudioAttributes(
                                                                    new AudioAttributes.Builder()
                                                                            .setContentType(
                                                                                    AudioAttributes
                                                                                            .CONTENT_TYPE_MUSIC)
                                                                            .build())
                                                            .build();

                                            flag =
                                                    audioManager.requestAudioFocus(
                                                                    audioFocusRequest)
                                                            == AudioManager
                                                                    .AUDIOFOCUS_REQUEST_GRANTED;
                                        }

                                        if (flag) {

                                            startService(
                                                    new Intent(
                                                            MediaPlaybackService.this,
                                                            MediaBrowserService.class));
                                            mediaSession.setActive(true);
                                            mediaPlayer.start();

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                mediaSession.setPlaybackState(
                                                        new PlaybackStateCompat.Builder()
                                                                .setState(
                                                                        PlaybackStateCompat
                                                                                .STATE_PLAYING,
                                                                        mediaPlayer
                                                                                .getCurrentPosition(),
                                                                        mediaPlayer
                                                                                .getPlaybackParams()
                                                                                .getSpeed())
                                                                .setActions(
                                                                        PlaybackStateCompat
                                                                                .ACTION_SEEK_TO)
                                                                .build());
                                            } else {
                                                mediaSession.setPlaybackState(
                                                        new PlaybackStateCompat.Builder()
                                                                .setState(
                                                                        PlaybackStateCompat
                                                                                .STATE_PLAYING,
                                                                        mediaPlayer
                                                                                .getCurrentPosition(),
                                                                        1F)
                                                                .setActions(
                                                                        PlaybackStateCompat
                                                                                .ACTION_SEEK_TO)
                                                                .build());
                                            }

                                            registerReceiver(
                                                    becomingNoisyReceiver,
                                                    new IntentFilter(
                                                            AudioManager
                                                                    .ACTION_AUDIO_BECOMING_NOISY));
                                            registerReceiver(
                                                    mediaButtonActionReceiver,
                                                    new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

                                            IntentFilter notificationFilter = new IntentFilter();
                                            notificationFilter.addAction(ACTION_PLAY_PAUSE);
                                            notificationFilter.addAction(ACTION_CANCEL);
                                            notificationFilter.addAction(ACTION_REWIND);
                                            notificationFilter.addAction(ACTION_SEEK);

                                            if (Build.VERSION.SDK_INT
                                                    >= Build.VERSION_CODES.TIRAMISU)
                                                registerReceiver(
                                                        notificationReceiver,
                                                        notificationFilter,
                                                        RECEIVER_NOT_EXPORTED);
                                            else
                                                registerReceiver(
                                                        notificationReceiver, notificationFilter);

                                            audio =
                                                    AudioUtils.getMetaData(
                                                            MediaPlaybackService.this,
                                                            String.valueOf(
                                                                    mediaPlayer.getDuration()),
                                                            uri);

                                            Log.e("vishnu", "onPlayFromUri(): " + audio);

                                            mediaSession.setMetadata(audio.getMediaMetadata());

                                            updateHandler.postDelayed(updateRunnable, 0);

                                            startForeground(NOTIFICATION_ID, getNotification());
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

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            mediaSession.setPlaybackState(
                                                    new PlaybackStateCompat.Builder()
                                                            .setState(
                                                                    PlaybackStateCompat
                                                                            .STATE_PLAYING,
                                                                    mediaPlayer
                                                                            .getCurrentPosition(),
                                                                    mediaPlayer
                                                                            .getPlaybackParams()
                                                                            .getSpeed())
                                                            .setActions(
                                                                    PlaybackStateCompat
                                                                            .ACTION_SEEK_TO)
                                                            .build());
                                        } else {
                                            mediaSession.setPlaybackState(
                                                    new PlaybackStateCompat.Builder()
                                                            .setState(
                                                                    PlaybackStateCompat
                                                                            .STATE_PLAYING,
                                                                    mediaPlayer
                                                                            .getCurrentPosition(),
                                                                    1F)
                                                            .setActions(
                                                                    PlaybackStateCompat
                                                                            .ACTION_SEEK_TO)
                                                            .build());
                                        }

                                        updateHandler.postDelayed(this, 10);
                                    }
                                };
                    }

                    @Override
                    public void onPlay() {
                        super.onPlay();

                        mediaPlayer.start();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(
                                                    PlaybackStateCompat.STATE_PLAYING,
                                                    mediaPlayer.getCurrentPosition(),
                                                    mediaPlayer.getPlaybackParams().getSpeed())
                                            .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                            .build());
                        } else {
                            mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(
                                                    PlaybackStateCompat.STATE_PLAYING,
                                                    mediaPlayer.getCurrentPosition(),
                                                    1F)
                                            .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                            .build());
                        }

                        updateHandler.postDelayed(updateRunnable, 10);

                        startForeground(NOTIFICATION_ID, getNotification());
                    }

                    @Override
                    public void onPause() {
                        super.onPause();

                        mediaPlayer.pause();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(
                                                    PlaybackStateCompat.STATE_PAUSED,
                                                    mediaPlayer.getCurrentPosition(),
                                                    mediaPlayer.getPlaybackParams().getSpeed())
                                            .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                            .build());
                        } else {
                            mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(
                                                    PlaybackStateCompat.STATE_PAUSED,
                                                    mediaPlayer.getCurrentPosition(),
                                                    1F)
                                            .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                            .build());
                        }

                        stopForeground(false);

                        updateHandler.removeCallbacks(updateRunnable);

                        startForeground(NOTIFICATION_ID, getNotification());
                    }

                    @Override
                    public void onStop() {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                                    .abandonAudioFocusRequest(audioFocusRequest);

                        mediaSession.setActive(false);

                        mediaPlayer.release();
                        updateHandler.removeCallbacks(updateRunnable);

                        stopSelf();

                        stopForeground(true);

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

                            mediaSession.setPlaybackState(
                                    new PlaybackStateCompat.Builder()
                                            .setState(
                                                    mediaSession
                                                            .getController()
                                                            .getPlaybackState()
                                                            .getState(),
                                                    mediaPlayer.getCurrentPosition(),
                                                    speed)
                                            .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                            .build());
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mediaSession.setPlaybackState(
                                new PlaybackStateCompat.Builder()
                                        .setState(
                                                PlaybackStateCompat.STATE_STOPPED,
                                                mediaPlayer.getCurrentPosition(),
                                                mediaPlayer.getPlaybackParams().getSpeed())
                                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                        .build());
                    } else {
                        mediaSession.setPlaybackState(
                                new PlaybackStateCompat.Builder()
                                        .setState(
                                                PlaybackStateCompat.STATE_STOPPED,
                                                mediaPlayer.getCurrentPosition(),
                                                1F)
                                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                                        .build());
                    }

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
        unregisterReceiver(notificationReceiver);
        super.onDestroy();
    }

    private Notification getNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel notificationChannel =
                    new NotificationChannel(
                            "DMPChannel", "DialogMusicPlayer", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription(
                    "Default notification channel for DialogMusicPlayer");
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            getSystemService(NotificationManager.class)
                    .createNotificationChannel(notificationChannel);
        }

        // https://stackoverflow.com/questions/63501425/java-android-media-player-notification
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(MediaPlaybackService.this, "DMPChannel")
                        .setStyle(
                                new androidx.media.app.NotificationCompat.MediaStyle()
                                        .setMediaSession(mediaSession.getSessionToken())
                                        .setShowCancelButton(true)
                                        .setCancelButtonIntent(
                                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                        MediaPlaybackService.this,
                                                        PlaybackStateCompat.ACTION_STOP))
                                        .setShowActionsInCompactView(0, 1, 2))
                        .setColor(
                                ContextCompat.getColor(
                                        MediaPlaybackService.this, R.color.accentColor))
                        .setSmallIcon(R.drawable.ic_icon)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        this,
                                        REQUEST_CODE,
                                        new Intent(this, MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                        PendingIntent.FLAG_CANCEL_CURRENT
                                                | PendingIntent.FLAG_IMMUTABLE))
                        .setContentTitle("DMP")
                        .setContentTitle(
                                audio.getMediaMetadata()
                                        .getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
                        .setContentText(
                                audio.getMediaMetadata()
                                        .getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
                        .setAutoCancel(false)
                        .setDeleteIntent(
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        this, PlaybackStateCompat.ACTION_STOP))
                        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                        .addAction(
                                new NotificationCompat.Action(
                                        R.drawable.ic_rewind,
                                        "Rewind",
                                        getPendingIntent(ACTION_REWIND)))
                        .addAction(
                                mediaSession.getController().getPlaybackState().getState()
                                                == PlaybackStateCompat.STATE_PLAYING
                                        ? new NotificationCompat.Action(
                                                R.drawable.ic_pause,
                                                "Pause",
                                                getPendingIntent(ACTION_PLAY_PAUSE))
                                        : new NotificationCompat.Action(
                                                R.drawable.ic_play,
                                                "Play",
                                                getPendingIntent(ACTION_PLAY_PAUSE)))
                        .addAction(
                                new NotificationCompat.Action(
                                        R.drawable.ic_seek, "Seek", getPendingIntent(ACTION_SEEK)))
                        .addAction(
                                new NotificationCompat.Action(
                                        R.drawable.ic_clear,
                                        "Close",
                                        getPendingIntent(ACTION_CANCEL)));

        return builder.build();
    }

    private PendingIntent getPendingIntent(String action) {
        return PendingIntent.getBroadcast(
                this,
                REQUEST_CODE,
                new Intent(action).setPackage(getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PLAY_PAUSE.equals(intent.getAction())) {
                if (mediaSession.getController().getPlaybackState().getState()
                        == PlaybackStateCompat.STATE_PLAYING)
                    mediaSession.getController().getTransportControls().pause();
                else if (mediaSession.getController().getPlaybackState().getState()
                        == PlaybackStateCompat.STATE_PAUSED)
                    mediaSession.getController().getTransportControls().play();

                startForeground(NOTIFICATION_ID, getNotification());
            } else if (ACTION_CANCEL.equals(intent.getAction()))
                mediaSession.getController().getTransportControls().stop();
            else if (ACTION_REWIND.equals(intent.getAction()))
                mediaSession
                        .getController()
                        .getTransportControls()
                        .seekTo(
                                mediaSession.getController().getPlaybackState().getPosition()
                                        - 10000);
            else if (ACTION_SEEK.equals(intent.getAction()))
                mediaSession
                        .getController()
                        .getTransportControls()
                        .seekTo(
                                mediaSession.getController().getPlaybackState().getPosition()
                                        + 10000);
        }
    }
}
