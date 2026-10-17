package com.astroempire.game;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        gameView = new WebView(this);
        gameView.setBackgroundColor(Color.BLACK);
        gameView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gameView.setVerticalScrollBarEnabled(false);
        gameView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        gameView.addJavascriptInterface(new AndroidBridge(), "AndroidApp");
        gameView.setWebViewClient(new WebViewClient());
        gameView.setWebChromeClient(new WebChromeClient());
        setContentView(gameView);

        try {
            String html = readAsset("index.html");
            // Stable HTTPS-like origin makes DOM/localStorage reliable while the app remains fully offline.
            gameView.loadDataWithBaseURL(
                    "https://astroempire.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );
        } catch (IOException e) {
            gameView.loadData(
                    "<html><body style='background:#050b18;color:white;font-family:sans-serif;padding:24px'>" +
                    "Не удалось загрузить AstroEmpire.<br><br>" + e.getMessage() +
                    "</body></html>",
                    "text/html",
                    "UTF-8"
            );
        }
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public void setLandscape() {
            runOnUiThread(() -> {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                enterImmersiveMode();
            });
        }
    }

    private String readAsset(String name) throws IOException {
        StringBuilder out = new StringBuilder(7_500_000);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8), 64 * 1024)) {
            char[] buffer = new char[64 * 1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                out.append(buffer, 0, count);
            }
        }
        return out.toString();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.onResume();
        enterImmersiveMode();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (gameView == null) {
            super.onBackPressed();
            return;
        }

        String js = "(function(){" +
                "try{" +
                "var visible=function(e){return !!(e && (e.offsetWidth||e.offsetHeight||e.getClientRects().length));};" +
                "var btns=[].slice.call(document.querySelectorAll('button'));" +
                "var close=btns.find(function(b){" +
                "if(!visible(b))return false;" +
                "var t=(b.textContent||'').trim().toLowerCase();" +
                "var id=(b.id||'').toLowerCase();" +
                "var c=(b.className||'').toString().toLowerCase();" +
                "return t==='×'||t==='закрыть'||id.indexOf('close')>=0||c.indexOf('close')>=0;" +
                "});" +
                "if(close){close.click();return true;}" +
                "return false;" +
                "}catch(e){return false;}" +
                "})()";

        gameView.evaluateJavascript(js, value -> {
            if (!"true".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }
}
