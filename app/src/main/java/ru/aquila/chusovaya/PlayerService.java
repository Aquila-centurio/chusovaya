package ru.aquila.chusovaya;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Foreground media service: keeps the process (and the WebView audio) alive with the screen off. */
public class PlayerService extends Service {
    static final String ACT_PLAY = "play", ACT_PAUSED = "paused", ACT_UPDATE = "update", ACT_TOGGLE = "toggle", ACT_STOP = "stop";
    static String title = "Чусовая", text = "Северный лес";
    private static final String CH = "chusovaya_music";
    private static final int ID = 7;
    private PowerManager.WakeLock lock;
    private AudioManager am;
    private AudioFocusRequest focus;
    private boolean playing = false;

    @Override public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        NotificationChannel c = new NotificationChannel(CH, "Музыка", NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        lock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chusovaya:music");
    }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        String a = in == null ? ACT_PLAY : in.getAction();
        if (a == null) a = ACT_PLAY;
        switch (a) {
            case ACT_PLAY: playing = true; grabFocus(); if (!lock.isHeld()) lock.acquire(); show(true); break;
            case ACT_PAUSED: playing = false; if (lock.isHeld()) lock.release(); show(false); break;
            case ACT_UPDATE: if (playing) show(true); break;
            case ACT_TOGGLE:
                if (MainActivity.instance != null) MainActivity.instance.runJs("window.__toggle&&window.__toggle(" + (playing ? "false" : "true") + ")");
                break;
            case ACT_STOP:
                if (MainActivity.instance != null) MainActivity.instance.runJs("window.__toggle&&window.__toggle(false)");
                playing = false; if (lock.isHeld()) lock.release(); stopForeground(true); stopSelf(); break;
        }
        return START_NOT_STICKY;
    }

    private void grabFocus() {
        AudioAttributes at = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(at)
                .setOnAudioFocusChangeListener(ch -> {
                    if ((ch == AudioManager.AUDIOFOCUS_LOSS || ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) && playing && MainActivity.instance != null)
                        MainActivity.instance.runJs("window.__toggle&&window.__toggle(false)");
                }).build();
        am.requestAudioFocus(focus);
    }

    private PendingIntent act(String a, int rc) {
        Intent i = new Intent(this, PlayerService.class).setAction(a);
        return PendingIntent.getService(this, rc, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void show(boolean on) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_note).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setOngoing(on).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_note), on ? "Пауза" : "Играть", act(ACT_TOGGLE, 1)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_note), "Закрыть", act(ACT_STOP, 2)).build());
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(ID, n);
        if (!on) stopForeground(false);
    }

    @Override public void onDestroy() { if (lock != null && lock.isHeld()) lock.release(); if (focus != null) am.abandonAudioFocusRequest(focus); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
