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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelperService extends Service {

    public static final String SESSION_TOKEN_EXTRA = "phone.vishnu.dialogmusicplayer.session_token";

    public static final int NOTIFICATION_ID = 2;

    public static final String ACTION_PLAY_PAUSE = "phone.vishnu.dialogmusicplayer.playPause";
    public static final String ACTION_REPLAY = "phone.vishnu.dialogmusicplayer.replay";
    public static final String ACTION_CANCEL = "phone.vishnu.dialogmusicplayer.cancel";
    public static final String ACTION_REWIND = "phone.vishnu.dialogmusicplayer.rewind";
    public static final String ACTION_SEEK = "phone.vishnu.dialogmusicplayer.seek";
    public static final String ACTION_STOP = "phone.vishnu.dialogmusicplayer.stop";

    public static final int REQUEST_CODE = 200;

    private MediaControllerCompat mediaController;

    private NotificationReceiver notificationReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        MediaSessionCompat.Token sessionToken = intent.getParcelableExtra(SESSION_TOKEN_EXTRA);

        if (sessionToken == null) return super.onStartCommand(intent, flags, startId);

        mediaController = new MediaControllerCompat(this, sessionToken);

        notificationReceiver = new NotificationReceiver();

        mediaController.registerCallback(
                new MediaControllerCompat.Callback() {

                    @Override
                    public void onMetadataChanged(MediaMetadataCompat metadata) {
                        super.onMetadataChanged(metadata);
                    }

                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                        super.onPlaybackStateChanged(state);
                    }

                    @Override
                    public void onSessionDestroyed() {
                        super.onSessionDestroyed();
                        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                                .cancelAll();
                        mediaController.unregisterCallback(this);
                    }
                });

        IntentFilter notificationFilter = new IntentFilter();
        notificationFilter.addAction(NotificationHelperService.ACTION_PLAY_PAUSE);
        notificationFilter.addAction(NotificationHelperService.ACTION_REPLAY);
        notificationFilter.addAction(NotificationHelperService.ACTION_CANCEL);
        notificationFilter.addAction(NotificationHelperService.ACTION_REWIND);
        notificationFilter.addAction(NotificationHelperService.ACTION_SEEK);

        registerReceiver(notificationReceiver, notificationFilter);

        startForeground(NOTIFICATION_ID, getNotification());

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        //        unregisterReceiver(notificationReceiver);
        super.onDestroy();
    }

    public Notification getNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel notificationChannel =
                    new NotificationChannel(
                            "DMPChannel", "DialogMusicPlayer", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription(
                    "Default notification channel for DialogMusicPlayer");
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            this.getSystemService(NotificationManager.class)
                    .createNotificationChannel(notificationChannel);
        }

        // https://stackoverflow.com/questions/63501425/java-android-media-player-notification
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "DMPChannel")
                        .setStyle(
                                new androidx.media.app.NotificationCompat.MediaStyle()
                                        .setMediaSession(mediaController.getSessionToken())
                                        .setShowCancelButton(true)
                                        .setCancelButtonIntent(getPendingIntent(ACTION_STOP))
                                        .setShowActionsInCompactView(0, 1, 2))
                        .setColor(ContextCompat.getColor(this, R.color.accentColor))
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
                                mediaController
                                        .getMetadata()
                                        .getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
                        .setContentText(
                                mediaController
                                        .getMetadata()
                                        .getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
                        .setAutoCancel(false)
                        .setDeleteIntent(getPendingIntent(ACTION_STOP))
                        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                        .addAction(
                                new NotificationCompat.Action(
                                        R.drawable.ic_rewind,
                                        "Rewind",
                                        getPendingIntent(ACTION_REWIND)))
                        .addAction(
                                mediaController.getPlaybackState().getState()
                                                == PlaybackStateCompat.STATE_STOPPED
                                        ? new NotificationCompat.Action(
                                                R.drawable.ic_replay,
                                                "Replay",
                                                getPendingIntent(ACTION_REPLAY))
                                        : mediaController.getPlaybackState().getState()
                                                        == PlaybackStateCompat.STATE_PAUSED
                                                ? new NotificationCompat.Action(
                                                        R.drawable.ic_play,
                                                        "Play",
                                                        getPendingIntent(ACTION_PLAY_PAUSE))
                                                : new NotificationCompat.Action(
                                                        R.drawable.ic_pause,
                                                        "Pause",
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
                new Intent(action).setPackage(this.getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NotificationHelperService.ACTION_PLAY_PAUSE.equals(intent.getAction())) {
                if (mediaController.getPlaybackState().getState()
                        == PlaybackStateCompat.STATE_PLAYING)
                    mediaController.getTransportControls().pause();
                else if (mediaController.getPlaybackState().getState()
                        == PlaybackStateCompat.STATE_PAUSED)
                    mediaController.getTransportControls().play();

                startForeground(NOTIFICATION_ID, getNotification());
            } else if (NotificationHelperService.ACTION_REPLAY.equals(intent.getAction()))
                mediaController.getTransportControls().play();
            else if (NotificationHelperService.ACTION_CANCEL.equals(intent.getAction()))
                mediaController.getTransportControls().stop();
            else if (NotificationHelperService.ACTION_REWIND.equals(intent.getAction()))
                mediaController
                        .getTransportControls()
                        .seekTo(mediaController.getPlaybackState().getPosition() - 10000);
            else if (NotificationHelperService.ACTION_SEEK.equals(intent.getAction()))
                mediaController
                        .getTransportControls()
                        .seekTo(mediaController.getPlaybackState().getPosition() + 10000);
            else if (NotificationHelperService.ACTION_STOP.equals(intent.getAction())) {

                mediaController.getTransportControls().stop();

                stopForeground(true);
                stopSelf();
            }
        }
    }
}
