package com.hadid.islamiccal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_PERM = 11;
    private static final int REQ_FILE = 12;

    private WebView web;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private AlertDialog dialog;
    private boolean askedOnce = false;
    private android.webkit.ValueCallback<Uri[]> fileCb;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF022C22);
        web = new WebView(this);
        web.setBackgroundColor(0xFF022C22);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Content must not sit under the status/navigation bars (edge-to-edge is enforced on Android 15).
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (hasLocationPermission()) {
                    cb.invoke(origin, true, false);
                } else {
                    geoCb = cb;
                    geoOrigin = origin;
                    requestNeeded();
                }
            }

            // <input type="file"> support (own adhan / alarm tone, backup files)
            @Override
            public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("(window.onNativeBack&&window.onNativeBack())?1:0", v -> {
                    if (!"1".equals(v)) finish();
                });
            }
        });

        web.loadUrl(START);
        AlarmReceiver.ensureChannels(this);
        requestNeeded();
        Scheduler.registerAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    /* ---------- permissions & location ---------- */

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean locationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /** Ask for whatever is missing: location permission, notification permission, then GPS switch. */
    private void requestNeeded() {
        List<String> need = new ArrayList<>();
        if (!hasLocationPermission()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERM);
        } else {
            askGpsIfOff();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        fileCb = null;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_PERM) return;
        boolean ok = hasLocationPermission();
        if (geoCb != null) {
            geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
        }
        if (!ok) {
            // permanently denied -> send the user to app settings
            boolean canAsk = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (!canAsk) showDialog(R.string.perm_title, R.string.perm_msg, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } else {
            askGpsIfOff();
            web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
        }
    }

    /** Automatic reminder that GPS is off: shown at most once a week, never on every launch. */
    private static final long GPS_ASK_GAP = 7L * 24 * 60 * 60 * 1000;

    private void askGpsIfOff() {
        if (!hasLocationPermission() || locationEnabled()) return;
        android.content.SharedPreferences sp = getSharedPreferences("prompts", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("gps_ask", 0) < GPS_ASK_GAP) return;
        sp.edit().putLong("gps_ask", now).apply();
        showDialog(R.string.loc_title, R.string.loc_msg, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void showDialog(int title, int msg, Intent action) {
        if (isFinishing() || (dialog != null && dialog.isShowing())) return;
        askedOnce = true;
        dialog = new AlertDialog.Builder(this)
                .setTitle(title).setMessage(msg).setCancelable(true)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    try { startActivity(action); } catch (Exception ignored) { }
                })
                .setNegativeButton(R.string.later, null)
                .show();
    }

    /* ---------- one-tap update: download APK and open installer ---------- */

    private long dlId = -1;
    private final android.os.Handler dlHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void apkStatus(String k, int p) {
        web.evaluateJavascript("window.onApkStatus&&window.onApkStatus('" + k + "'," + p + ")", null);
    }

    private void startApkDownload(String url) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            apkStatus("perm", 0);
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
            return;
        }
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "islamic-calendar.apk");
            if (f.exists()) f.delete();
            android.app.DownloadManager.Request r = new android.app.DownloadManager.Request(Uri.parse(url));
            r.setTitle("Islamic Calendar update");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE);
            r.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, "islamic-calendar.apk");
            android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dlId = dm.enqueue(r);
            apkStatus("dl", 0);
            pollDownload();
        } catch (Exception e) {
            apkStatus("err", 0);
        }
    }

    private void pollDownload() {
        dlHandler.postDelayed(() -> {
            android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            android.database.Cursor c = dm.query(new android.app.DownloadManager.Query().setFilterById(dlId));
            if (c == null) { apkStatus("err", 0); return; }
            try {
                if (!c.moveToFirst()) { apkStatus("err", 0); return; }
                int st = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                long got = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long tot = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (st == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    apkStatus("ok", 100);
                    Uri u = dm.getUriForDownloadedFile(dlId);
                    if (u == null) { apkStatus("err", 0); return; }
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(u, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(i); } catch (Exception e) { apkStatus("err", 0); }
                } else if (st == android.app.DownloadManager.STATUS_FAILED) {
                    apkStatus("err", 0);
                } else {
                    apkStatus("dl", tot > 0 ? (int) (got * 100 / tot) : 0);
                    pollDownload();
                }
            } finally {
                c.close();
            }
        }, 600);
    }

    /* ---------- share a generated file (timetable image / PDF) ---------- */

    private void doShare(String b64, String name, String mime) {
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            java.io.File dir = new java.io.File(getCacheDir(), "share");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { o.write(data); }
            Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, null));
        } catch (Exception e) {
            web.evaluateJavascript("window.onShareErr&&window.onShareErr()", null);
        }
    }

    /* ---------- JS bridge ---------- */

    private final class Bridge {
        @JavascriptInterface
        public void setSchedule(String json) {
            Scheduler.set(MainActivity.this, json);
        }

        @JavascriptInterface
        public String consumeFired() {
            return Scheduler.consumeFired(MainActivity.this);
        }

        @JavascriptInterface
        public void setWidget(String json) {
            getSharedPreferences("widget", MODE_PRIVATE).edit().putString("data", json).apply();
            CountdownWidget.refreshAll(MainActivity.this);
        }

        @JavascriptInterface
        public void shareFile(String b64, String name, String mime) {
            runOnUiThread(() -> doShare(b64, name, mime));
        }

        @JavascriptInterface
        public void installApk(String url) {
            if (url == null || !url.startsWith("https://github.com/")) return;
            runOnUiThread(() -> startApkDownload(url));
        }

        @JavascriptInterface
        public void fixLocation() {
            runOnUiThread(() -> {
                if (!hasLocationPermission()) {
                    boolean canAsk = ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
                    if (askedOnce && !canAsk) {
                        showDialog(R.string.perm_title, R.string.perm_msg, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } else {
                        requestNeeded();
                    }
                } else if (!locationEnabled()) {
                    showDialog(R.string.loc_title, R.string.loc_msg, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
        }
    }
}
