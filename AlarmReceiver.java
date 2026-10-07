package com.hadid.islamiccal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    static final String CH_ADHAN = "adhan_v1", CH_ALARM = "alarm_v1";

    @Override
    public void onReceive(Context c, Intent in) {
        String title = in.getStringExtra("n"), body = in.getStringExtra("b"), kind = in.getStringExtra("k");
        long aid = in.getLongExtra("aid", 0);
        boolean alarm = "alarm".equals(kind);
        Scheduler.markFired(c, aid);
        ensureChannels(c);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, alarm ? CH_ALARM : CH_ADHAN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title == null || title.isEmpty() ? c.getString(R.string.ch_alarm) : title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(alarm ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(new long[]{0, 400, 200, 400});
        if (alarm) b.setTimeoutAfter(60_000);
        Notification n = b.build();
        if (alarm) n.flags |= Notification.FLAG_INSISTENT;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (SecurityException ignored) { }
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        // Put an audio file named adhan.mp3 into res/raw to use a real adhan sound.
        int raw = c.getResources().getIdentifier("adhan", "raw", c.getPackageName());
        Uri adhan = raw != 0 ? Uri.parse("android.resource://" + c.getPackageName() + "/" + raw)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel a = new NotificationChannel(CH_ADHAN, c.getString(R.string.ch_adhan), NotificationManager.IMPORTANCE_HIGH);
        a.setSound(adhan, aa);
        a.enableVibration(true);
        nm.createNotificationChannel(a);

        AudioAttributes al = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel b = new NotificationChannel(CH_ALARM, c.getString(R.string.ch_alarm), NotificationManager.IMPORTANCE_HIGH);
        b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), al);
        b.enableVibration(true);
        nm.createNotificationChannel(b);
    }
}
