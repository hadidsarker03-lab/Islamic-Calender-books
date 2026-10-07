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

    private WebView web;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private AlertDialog dialog;
    private boolean askedOnce = false;

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

    private void askGpsIfOff() {
        if (hasLocationPermission() && !locationEnabled()) {
            showDialog(R.string.loc_title, R.string.loc_msg, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
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
