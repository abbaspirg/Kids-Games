package com.fitstreak.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Hosts the workout app (assets/index.html) in a full-screen WebView. */
public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0F172A);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // workout history lives in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);

        web.loadUrl("file:///android_asset/index.html");

        Reminders.createChannel(this);
        Reminders.scheduleAll(this);
        askNotificationPermission();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onAppResume && onAppResume()", null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Let the page close an open screen first; exit only when it says it has nothing to close.
        web.evaluateJavascript("window.handleBack ? handleBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** Methods the page can call as window.Android.xxx(). */
    private class Bridge {
        @JavascriptInterface
        public void setReminders(boolean morningOn, int hour, int minute, boolean eveningOn) {
            Reminders.saveSettings(MainActivity.this, morningOn, hour, minute, eveningOn);
            Reminders.scheduleAll(MainActivity.this);
            if (morningOn || eveningOn) runOnUiThread(MainActivity.this::askNotificationPermission);
        }

        @JavascriptInterface
        public void sync(String lastDoneDate, int streak) {
            Reminders.saveProgress(MainActivity.this, lastDoneDate, streak);
        }

        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }
    }
}
