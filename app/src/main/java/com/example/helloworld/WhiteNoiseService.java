package com.example.helloworld;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class WhiteNoiseService extends Service {

    private static final String CHANNEL_ID = "whitenoise_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PLAY = "com.example.whitenoise.PLAY";
    public static final String ACTION_STOP = "com.example.whitenoise.STOP";
    public static final String EXTRA_RES_ID = "extra_res_id";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_IS_CUSTOM = "extra_is_custom";
    public static final String EXTRA_SOUND_NAME = "extra_sound_name";

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            String soundName = intent.getStringExtra(EXTRA_SOUND_NAME);
            if (soundName == null) soundName = "白噪音";

            if (ACTION_PLAY.equals(action)) {
                boolean isCustom = intent.getBooleanExtra(EXTRA_IS_CUSTOM, false);
                if (isCustom) {
                    String url = intent.getStringExtra(EXTRA_URL);
                    playUrl(url);
                } else {
                    int resId = intent.getIntExtra(EXTRA_RES_ID, R.raw.rain);
                    playResource(resId);
                }
                startForeground(NOTIFICATION_ID, buildNotification(soundName));
            } else if (ACTION_STOP.equals(action)) {
                stopPlaying();
            }
        }
        return START_STICKY;
    }

    private void playResource(int resId) {
        stopPlayingInternal();
        try {
            mediaPlayer = MediaPlayer.create(this, resId);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                isPlaying = true;
            }
        } catch (Exception e) {
            Log.e("WNS", "play error: " + e.getMessage());
        }
    }

    private void playUrl(String url) {
        stopPlayingInternal();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> mp.start());
            isPlaying = true;
        } catch (Exception e) {
            Log.e("WNS", "url play error: " + e.getMessage());
        }
    }

    private void stopPlayingInternal() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void stopPlaying() {
        stopPlayingInternal();
        isPlaying = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private Notification buildNotification(String soundName) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("白噪音正在播放")
               .setContentText(soundName + " · 助您放松身心")
               .setSmallIcon(android.R.drawable.ic_media_play)
               .setContentIntent(pendingIntent)
               .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return builder.build();
        } else {
            return builder.getNotification();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "白噪音播放", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("后台白噪音播放通知");
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopPlayingInternal();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
