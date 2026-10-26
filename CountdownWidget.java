package com.hadid.islamiccal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: live countdown to sehri end / iftar. Data comes from the web app via AndroidApp.setWidget(). */
public class CountdownWidget extends AppWidgetProvider {
    static final String ACT_TICK = "com.hadid.islamiccal.WIDGET_TICK";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (ACT_TICK.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            refreshAll(c);
        }
    }

    @Override
    public void onDisabled(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tickIntent(c));
        } catch (Exception ignored) { }
    }

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CountdownWidget.class));
        if (ids == null || ids.length == 0) return;
        render(c, m, ids);
    }

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, CountdownWidget.class).setAction(ACT_TICK);
        return PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        String label = null, line = "", area = "", openTxt = "অ্যাপ খুলুন", hint = "সময় আপডেট করতে অ্যাপ একবার খুলুন";
        long target = 0;
        try {
            SharedPreferences sp = c.getSharedPreferences("widget", Context.MODE_PRIVATE);
            String raw = sp.getString("data", null);
            if (raw != null) {
                JSONObject o = new JSONObject(raw);
                area = o.optString("area", "");
                openTxt = o.optString("open", openTxt);
                hint = o.optString("hint", hint);
                JSONArray ev = o.getJSONArray("ev");
                for (int k = 0; k < ev.length(); k++) {
                    JSONObject e = ev.getJSONObject(k);
                    if (e.getLong("t") > now) {
                        target = e.getLong("t");
                        label = e.getString("lb");
                        line = e.optString("ln", "");
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_countdown);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 78, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.wRoot, pi);
        rv.setTextViewText(R.id.wArea, area);

        if (label == null) {
            rv.setTextViewText(R.id.wLabel, openTxt);
            rv.setViewVisibility(R.id.wCd, View.GONE);
            rv.setTextViewText(R.id.wLine, hint);
        } else {
            rv.setTextViewText(R.id.wLabel, label);
            rv.setViewVisibility(R.id.wCd, View.VISIBLE);
            if (Build.VERSION.SDK_INT >= 24) rv.setChronometerCountDown(R.id.wCd, true);
            rv.setChronometer(R.id.wCd, SystemClock.elapsedRealtime() + (target - now), null, true);
            rv.setTextViewText(R.id.wLine, line);
            scheduleTick(c, target + 1500);
        }
        m.updateAppWidget(ids, rv);
    }

    private static void scheduleTick(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tickIntent(c);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi); } catch (Exception ignored) { }
        }
    }
}
