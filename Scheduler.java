package com.hadid.islamiccal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores the schedule pushed from the web UI and registers exact alarms with AlarmManager. */
final class Scheduler {
    private static final String PREF = "sched";
    private static final int MAX = 120;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Replace stored schedule and re-register alarms. */
    static void set(Context c, String json) {
        cancelAll(c);
        prefs(c).edit().putString("list", json).apply();
        registerAll(c);
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        String old = prefs(c).getString("reg", "[]");
        try {
            JSONArray a = new JSONArray(old);
            for (int i = 0; i < a.length(); i++) {
                am.cancel(pending(c, a.getInt(i), null));
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString("reg", "[]").apply();
    }

    static void registerAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        List<JSONObject> items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("t") > now) items.add(o);
            }
        } catch (Exception ignored) { }
        Collections.sort(items, (x, y) -> Long.compare(x.optLong("t"), y.optLong("t")));
        JSONArray reg = new JSONArray();
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        for (int i = 0; i < items.size() && i < MAX; i++) {
            JSONObject o = items.get(i);
            int code = o.optString("id").hashCode();
            PendingIntent pi = pending(c, code, o);
            long t = o.optLong("t");
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                reg.put(code);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                reg.put(code);
            }
        }
        prefs(c).edit().putString("reg", reg.toString()).apply();
    }

    private static PendingIntent pending(Context c, int code, JSONObject o) {
        Intent i = new Intent(c, AlarmReceiver.class);
        if (o != null) {
            i.putExtra("n", o.optString("n"));
            i.putExtra("b", o.optString("b"));
            i.putExtra("k", o.optString("k"));
            i.putExtra("aid", o.optLong("aid", 0));
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, code, i, flags);
    }

    /** Remember that a one-time alarm has fired so the web UI can switch it off. */
    static void markFired(Context c, long aid) {
        if (aid == 0) return;
        try {
            JSONArray a = new JSONArray(prefs(c).getString("fired", "[]"));
            a.put(aid);
            prefs(c).edit().putString("fired", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    static String consumeFired(Context c) {
        String s = prefs(c).getString("fired", "[]");
        prefs(c).edit().putString("fired", "[]").apply();
        return s;
    }
}
