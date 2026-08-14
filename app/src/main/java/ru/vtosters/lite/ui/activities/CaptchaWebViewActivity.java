package ru.vtosters.lite.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CaptchaWebViewActivity extends AppCompatActivity {
    public static final String EXTRA_REDIRECT_URI = "extra_redirect_uri";
    public static final String EXTRA_REMIXSTLID = "extra_remixstlid";
    public static final String RESULT_SUCCESS_TOKEN = "result_success_token";
    public static final String RESULT_REMIXSTLID = "result_remixstlid";

    private WebView webView;

    public static void start(Context context, String redirectUri) {
        start(context, redirectUri, null);
    }

    public static void start(Context context, String redirectUri, @Nullable String remixstlid) {
        if (context == null || redirectUri == null || redirectUri.isEmpty()) {
            return;
        }

        Intent intent = new Intent(context, CaptchaWebViewActivity.class);
        intent.putExtra(EXTRA_REDIRECT_URI, redirectUri);
        if (remixstlid != null && !remixstlid.isEmpty()) {
            intent.putExtra(EXTRA_REMIXSTLID, remixstlid);
        }

        if (context instanceof Activity) {
            ((Activity) context).startActivityForResult(intent, 1001);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        setContentView(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return handleUrl(request.getUrl() != null ? request.getUrl().toString() : null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleUrl(url);
            }
        });

        String redirectUri = getIntent().getStringExtra(EXTRA_REDIRECT_URI);
        if (redirectUri == null || redirectUri.isEmpty()) {
            finish();
            return;
        }

        webView.loadUrl(redirectUri);
    }

    private boolean handleUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        Uri uri = Uri.parse(url);
        String successToken = uri.getQueryParameter("success_token");
        String remixstlid = uri.getQueryParameter("remixstlid");
        if (successToken == null || successToken.isEmpty()) {
            String fragment = uri.getFragment();
            if (fragment != null && !fragment.isEmpty()) {
                Uri fragmentUri = Uri.parse("https://vk.ru/" + fragment);
                successToken = fragmentUri.getQueryParameter("success_token");
                if (successToken == null || successToken.isEmpty()) {
                    successToken = fragmentUri.getQueryParameter("token");
                }
                if (remixstlid == null || remixstlid.isEmpty()) {
                    remixstlid = fragmentUri.getQueryParameter("remixstlid");
                }
            }
        }

        if (successToken != null && !successToken.isEmpty()) {
            Intent result = new Intent();
            result.putExtra(RESULT_SUCCESS_TOKEN, successToken);
            if (remixstlid != null && !remixstlid.isEmpty()) {
                result.putExtra(RESULT_REMIXSTLID, remixstlid);
            }
            setResult(Activity.RESULT_OK, result);
            finish();
            return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }
}
