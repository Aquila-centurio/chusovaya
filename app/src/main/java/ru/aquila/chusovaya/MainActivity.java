package ru.aquila.chusovaya;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static MainActivity instance;
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        instance = this;
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1A1F);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(web);
        immersive();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        web.loadUrl("file:///android_asset/index.html");
    }

    private void immersive() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    // WebView.onPause() is deliberately NOT called: the music must keep playing with the screen off.
    @Override protected void onStop() { super.onStop(); web.onResume(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        if (isFinishing()) { stopService(new Intent(this, PlayerService.class)); web.destroy(); }
    }

    void runJs(final String js) { runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); }); }

    class Bridge {
        @JavascriptInterface public void setPlaying(boolean on) {
            Intent i = new Intent(MainActivity.this, PlayerService.class);
            if (on) {
                i.setAction(PlayerService.ACT_PLAY);
                startForegroundService(i);
            } else {
                i.setAction(PlayerService.ACT_PAUSED);
                startService(i);
            }
        }
        @JavascriptInterface public void setTrack(String title, String text) {
            PlayerService.title = title; PlayerService.text = text;
            Intent i = new Intent(MainActivity.this, PlayerService.class);
            i.setAction(PlayerService.ACT_UPDATE);
            try { startService(i); } catch (Exception ignored) {}
        }
    }
}
