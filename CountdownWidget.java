package com.hadid.islamiccal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: clock, weather, current prayer, sehri / iftar countdown. Data comes from the web app via AndroidApp.setWidget(). */
public class CountdownWidget extends AppWidgetProvider {
    static final String ACT_TICK = "com.hadid.islamiccal.WIDGET_TICK";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        render(c, m, new int[]{id});
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

    private static JSONObject load(Context c) {
        try {
            String raw = c.getSharedPreferences("widget", Context.MODE_PRIVATE).getString("data", null);
            if (raw != null) return new JSONObject(raw);
        } catch (Exception ignored) { }
        return null;
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        JSONObject o = load(c);
        for (int id : ids) {
            Bundle opt = m.getAppWidgetOptions(id);
            int w = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            try {
                m.updateAppWidget(id, build(c, o, now, w >= 250));
            } catch (Exception ignored) { }
        }
        long b = nextBoundary(o, now);
        if (b > 0) scheduleTick(c, b + 1500);
    }

    private static void show(RemoteViews rv, int id, boolean on) {
        rv.setViewVisibility(id, on ? View.VISIBLE : View.GONE);
    }

    private static void cd(RemoteViews rv, int id, long target, long now) {
        rv.setViewVisibility(id, View.VISIBLE);
        rv.setChronometerCountDown(id, true);
        rv.setChronometer(id, SystemClock.elapsedRealtime() + (target - now), null, true);
    }

    static RemoteViews build(Context c, JSONObject o, long now, boolean wide) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_countdown);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 78, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.wRoot, pi);
        rv.setTextViewTextSize(R.id.wClock, TypedValue.COMPLEX_UNIT_SP, wide ? 40 : 30);
        rv.setTextViewTextSize(R.id.wAmPm, TypedValue.COMPLEX_UNIT_SP, wide ? 14 : 11);
        show(rv, R.id.wSr, wide);
        show(rv, R.id.wSs, wide);

        if (o == null) {
            show(rv, R.id.wTemp, false);
            rv.setTextViewText(R.id.wArea, "");
            show(rv, R.id.wGl, false);
            show(rv, R.id.wHj, false);
            show(rv, R.id.wPrayCd, false);
            show(rv, R.id.wPrayL, false);
            show(rv, R.id.wSehriRow, false);
            show(rv, R.id.wIftarRow, false);
            rv.setTextViewText(R.id.wPray, "অ্যাপ খুলুন");
            return rv;
        }

        JSONObject lb = o.optJSONObject("lb");
        if (lb == null) lb = new JSONObject();
        String temp = (o.optString("tp", "") + " " + o.optString("wx", "")).trim();
        rv.setTextViewText(R.id.wTemp, temp);
        show(rv, R.id.wTemp, !temp.isEmpty());
        rv.setTextViewText(R.id.wSr, o.optString("sr", ""));
        rv.setTextViewText(R.id.wSs, o.optString("ss", ""));
        rv.setTextViewText(R.id.wArea, o.optString("area", ""));

        rv.setTextViewText(R.id.wGl, lb.optString("gr", ""));
        show(rv, R.id.wGl, true);
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
        JSONObject hj = o.optJSONObject("hj");
        String hs = hj == null ? "" : hj.optString(key, "");
        rv.setTextViewText(R.id.wHj, hs.isEmpty() ? "" : lb.optString("hj", "") + " " + hs);
        show(rv, R.id.wHj, !hs.isEmpty());

        // current / next prayer
        String pName = null, nName = null;
        long pEnd = 0, nStart = Long.MAX_VALUE;
        JSONArray ph = o.optJSONArray("ph");
        if (ph != null) {
            for (int i = 0; i < ph.length(); i++) {
                JSONObject e = ph.optJSONObject(i);
                if (e == null) continue;
                long s = e.optLong("s"), en = e.optLong("e");
                if (s <= now && now < en) { pName = e.optString("n"); pEnd = en; break; }
                if (s > now && s < nStart) { nStart = s; nName = e.optString("n"); }
            }
        }
        if (pName != null) {
            rv.setTextViewText(R.id.wPray, pName + " " + lb.optString("run", ""));
            rv.setTextViewText(R.id.wPrayL, lb.optString("end", ""));
            show(rv, R.id.wPrayL, true);
            cd(rv, R.id.wPrayCd, pEnd, now);
        } else if (nName != null) {
            rv.setTextViewText(R.id.wPray, lb.optString("next", "") + ": " + nName);
            rv.setTextViewText(R.id.wPrayL, lb.optString("start", ""));
            show(rv, R.id.wPrayL, true);
            cd(rv, R.id.wPrayCd, nStart, now);
        } else {
            rv.setTextViewText(R.id.wPray, o.optString("open", "অ্যাপ খুলুন"));
            show(rv, R.id.wPrayL, false);
            show(rv, R.id.wPrayCd, false);
        }

        // sehri / iftar
        long ts = 0, ti = 0;
        String ms = "", mi = "";
        JSONArray ev = o.optJSONArray("ev");
        if (ev != null) {
            for (int i = 0; i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null || e.optLong("t") <= now) continue;
                String k = e.optString("k");
                if (k.equals("s") && ts == 0) { ts = e.optLong("t"); ms = e.optString("tm"); }
                if (k.equals("i") && ti == 0) { ti = e.optLong("t"); mi = e.optString("tm"); }
            }
        }
        show(rv, R.id.wSehriRow, ts != 0);
        show(rv, R.id.wIftarRow, ti != 0);
        rv.setTextViewText(R.id.wSehri, lb.optString("sehri", "") + "  " + ms);
        rv.setTextViewText(R.id.wIftar, lb.optString("iftar", "") + "  " + mi);
        boolean sFirst = ts != 0 && (ti == 0 || ts < ti);
        show(rv, R.id.wSehriL, sFirst);
        show(rv, R.id.wSehriCd, sFirst);
        show(rv, R.id.wIftarL, !sFirst && ti != 0);
        show(rv, R.id.wIftarCd, !sFirst && ti != 0);
        rv.setTextViewText(R.id.wSehriL, lb.optString("sl", ""));
        rv.setTextViewText(R.id.wIftarL, lb.optString("il", ""));
        if (sFirst) cd(rv, R.id.wSehriCd, ts, now);
        else if (ti != 0) cd(rv, R.id.wIftarCd, ti, now);
        return rv;
    }

    private static long nextBoundary(JSONObject o, long now) {
        if (o == null) return 0;
        long best = Long.MAX_VALUE;
        JSONArray ev = o.optJSONArray("ev");
        if (ev != null) for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.optJSONObject(i);
            if (e == null) continue;
            long t = e.optLong("t");
            if (t > now && t < best) best = t;
        }
        JSONArray ph = o.optJSONArray("ph");
        if (ph != null) for (int i = 0; i < ph.length(); i++) {
            JSONObject e = ph.optJSONObject(i);
            if (e == null) continue;
            long s = e.optLong("s"), en = e.optLong("e");
            if (s > now && s < best) best = s;
            if (en > now && en < best) best = en;
        }
        return best == Long.MAX_VALUE ? 0 : best;
    }

    private static void scheduleTick(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tickIntent(c);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi); } catch (Exception ignored) { }
        }
    }
}
