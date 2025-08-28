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



        // WebView settings
        WebSettings ws = webView.getSettings();
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setDatabaseEnabled(true);

        // Critical settings for OAuth to work properly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            CookieManager.getInstance().setAcceptCookie(true);
        }

        // Enable these for better storage handling
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setAllowFileAccessFromFileURLs(true);

        // Set a proper user agent (some sites check this)
        String defaultUserAgent = ws.getUserAgentString();
        ws.setUserAgentString(defaultUserAgent + " MyAppWebView/1.0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(true);
        }

        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());

        // Add JavaScript interface for auth communication
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

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

    private void setupSystemBars() {
        // Just set light status bar appearance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        }

        // Let the WebView handle its own insets naturally
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
            // Load the URL from the intent data (redirect back from authentication)
            String url = appLinkData.toString();
            webView.loadUrl(url);
        }
    }

    // JavaScript Interface for communication with web content
    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void handleAuthError(String error) {
            Log.d("WebViewAuth", "Authentication error: " + error);
            // Handle authentication errors from the web page
            runOnUiThread(() -> {
                if (error.contains("missing initial state") || error.contains("sessionStorage")) {
                    showError("Authentication Error",
                            "Please try signing in again. If the problem persists, clear your app data and try again.");
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void authSuccess(String userInfo) {
            Log.d("WebViewAuth", "Authentication successful: " + userInfo);
            // Handle successful authentication
        }
    }

    private class AppWebViewClient extends WebViewClient {
        private boolean isFirebaseAuthUrl(String url) {
            return url.contains("accounts.google.com") ||
                    url.contains("google.com/oauth") ||
                    url.contains("securetoken.googleapis.com") ||
                    url.contains("firebaseapp.com") ||
                    url.contains("__/auth/handler");
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Don't override URLs that stay within our domain or Firebase auth domains
            if (url.startsWith(URL) || url.startsWith("https://bithash.apps.adpumb.com") ||
                    isFirebaseAuthUrl(url)) {
                return false; // Let WebView handle it
            }

            // For all other external links, open in browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            isErrorShown = false;

            // Inject JavaScript to handle Firebase auth redirect issues
            if (isFirebaseAuthUrl(url)) {
                injectFirebaseAuthFix(view);
            }
        }

        private void injectFirebaseAuthFix(WebView view) {
            // JavaScript to handle Firebase authentication in WebView
            String js = "(function() {" +
                    "// Store original sessionStorage reference" +
                    "if (!window.originalSessionStorage) {" +
                    "  window.originalSessionStorage = window.sessionStorage;" +
                    "}" +

                    "// Override Firebase's redirect detection" +
                    "if (typeof firebase !== 'undefined') {" +
                    "  var originalAuth = firebase.auth;" +
                    "  if (originalAuth && originalAuth.Auth) {" +
                    "    var originalIsRedirect = originalAuth.Auth.prototype._isRedirect;" +
                    "    if (originalIsRedirect) {" +
                    "      originalAuth.Auth.prototype._isRedirect = function() {" +
                    "        return false; // Force Firebase to treat this as a popup flow" +
                    "      };" +
                    "    }" +
                    "  }" +
                    "}" +

                    "// Force popup behavior for Firebase UI" +
                    "if (typeof firebase !== 'undefined' && firebase.auth) {" +
                    "  firebase.auth().useDeviceLanguage();" +
                    "  // Try to detect if we're in a WebView environment" +
                    "  var isWebView = /WebView|Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);" +
                    "  if (isWebView) {" +
                    "    // Override signInWithRedirect to use signInWithPopup instead" +
                    "    var originalSignInWithRedirect = firebase.auth().signInWithRedirect;" +
                    "    if (originalSignInWithRedirect) {" +
                    "      firebase.auth().signInWithRedirect = function(provider) {" +
                    "        return firebase.auth().signInWithPopup(provider);" +
                    "      };" +
                    "    }" +
                    "  }" +
                    "}" +
                    "})();";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                view.evaluateJavascript(js, null);
            } else {
                view.loadUrl("javascript:" + js);
            }
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
            // You can use this to show progress if needed, but we're using a simple loading screen
            if (newProgress >= 80) {
                // When page is almost loaded, we'll wait for onPageFinished to hide the loader
                loadingProgress.setVisibility(View.GONE);
            }
        }

        @Override
        public void onConsoleMessage(String message, int lineNumber, String sourceID) {
            Log.d("WebViewConsole", message + " -- From line " + lineNumber + " of " + sourceID);

            // Check for Firebase authentication errors
            if (message.contains("missing initial state") ||
                    message.contains("sessionStorage") ||
                    message.contains("auth/redirect")) {
                runOnUiThread(() -> {
                    // Try to recover by reloading
                    webView.postDelayed(() -> {
                        webView.reload();
                    }, 1000);
                });
            }
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                    + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
            return true;
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