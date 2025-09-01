package com.example.bithash;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private RelativeLayout loadingLayout;
    private LinearLayout errorLayout;
    private ProgressBar loadingProgress;
    private TextView errorText;
    private TextView errorDetails;
    private Button retryBtn;

    private static final String URL = "https://bithash.apps.adpumb.com/";

    private boolean isErrorShown = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        webView = findViewById(R.id.webview);
        loadingLayout = findViewById(R.id.loadingLayout);
        errorLayout = findViewById(R.id.errorLayout);
        loadingProgress = findViewById(R.id.loadingProgress);
        errorText = findViewById(R.id.errorText);
        errorDetails = findViewById(R.id.errorDetails);
        retryBtn = findViewById(R.id.retryBtn);

        // Configure WebView with proper Android user agent
        configureWebView();

        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());

        retryBtn.setOnClickListener(v -> {
            hideError();
            checkNetworkAndLoad();
        });

        setupSystemBars();
        handleIntent(getIntent());

        // Check permissions
        checkPermissions();

        // Initial load
        checkNetworkAndLoad();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack() && !isErrorShown) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    private void configureWebView() {
        WebSettings ws = webView.getSettings();

        // Basic settings
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Set proper Android user agent (not desktop)
        String defaultUserAgent = ws.getUserAgentString();
        // Modify user agent to remove WebView markers but keep Android identity
  /*      String androidUserAgent = defaultUserAgent
                .replace("; wv)", ")") // Remove WebView indicator
                .replace("WebView", "") // Remove WebView text
                + " BithashApp/1.0"; */// Add app identifier

        ws.setUserAgentString(defaultUserAgent);
        Log.d("UserAgent", "Using User-Agent: " + defaultUserAgent);

        // Cookie handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            CookieManager.getInstance().setAcceptCookie(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(true);
        }
    }

    private void setupSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        webView.setFitsSystemWindows(true);
    }

    private void checkNetworkAndLoad() {
        if (isNetworkAvailable()) {
            loadUrl();
        } else {
            showError(getString(R.string.error_no_internet), getString(R.string.error_no_internet_message));
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void loadUrl() {
        loadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        webView.loadUrl(URL);
    }

    private void showError(String title, String message) {
        isErrorShown = true;
        loadingLayout.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        errorText.setText(title);
        errorDetails.setText(message);
    }

    private void hideError() {
        isErrorShown = false;
        errorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();

        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
            String url = appLinkData.toString();
            if (url.startsWith(URL)) {
                webView.loadUrl(url);
            }
        }
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Don't override URLs that stay within our domain
            if (url.startsWith(URL) || url.startsWith("https://bithash.apps.adpumb.com")) {
                return false;
            }

            // For all external links, open in browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            isErrorShown = false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Hide loading screen with a slight delay for a smoother transition
            view.postDelayed(() -> hideLoading(), 300);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                if (!isNetworkAvailable()) {
                    showError(getString(R.string.error_no_internet), getString(R.string.error_no_internet_message));
                } else {
                    showError("Failed to Load", "Unable to load the content. Please check internet and try again later.");
                }
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame()) {
                showError("Server Error", "The server encountered an error. Please try again later.");
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            showError("Security Error", "There was a security issue loading the page. Please check your connection and try again.");
        }
    }

    private class AppWebChromeClient extends android.webkit.WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress >= 80) {
                loadingProgress.setVisibility(View.GONE);
            }
        }

        @Override
        public void onConsoleMessage(String message, int lineNumber, String sourceID) {
            Log.d("WebViewConsole", message + " -- From line " + lineNumber + " of " + sourceID);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}