package com.smolish.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuthActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private boolean checking;
    private boolean transferring;
    private boolean finished;
    private boolean addAccount;
    private String initialIdentity;
    private String failedCookie = "";
    private long failedAt;
    private String transferError;
    private SecureSession session;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            checkSession();
            if (!finished) handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SecureSession(this);
        addAccount = getIntent().getBooleanExtra("addAccount", false);
        LinearLayout root = Ui.vertical(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(Ui.dp(this, 10), insets.getSystemWindowInsetTop() + Ui.dp(this, 10),
                    Ui.dp(this, 10), insets.getSystemWindowInsetBottom() + Ui.dp(this, 10));
            return insets;
        });
        LinearLayout actions = Ui.horizontal(this);
        IconView back = new IconView(this, "back");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> { if (browser.canGoBack()) browser.goBack(); else finish(); });
        actions.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        actions.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        IconView reload = new IconView(this, "refresh");
        reload.setContentDescription("Reload sign-in page");
        reload.setOnClickListener(v -> browser.reload());
        actions.addView(reload, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        actions.addView(Ui.primary(this, "Use session", v -> {
                    if (addAccount) checkSession(); else transferBrowserSession(null, null, true);
                }),
                new LinearLayout.LayoutParams(Ui.dp(this, 122), Ui.dp(this, 44)));
        Ui.add(root, actions);
        status = Ui.sub(this, addAccount
                ? "Use the Smolish account switcher in the page to add another account."
                : "Sign in on the Smolish website below.");
        Ui.add(root, status);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        browser.getSettings().setSupportMultipleWindows(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.addJavascriptInterface(new BrowserBridge(), "SmolishAndroidBridge");
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }
            @Override public void onPageFinished(WebView view, String url) { checkSession(); }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(AuthActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView child, WebResourceRequest request) {
                        if ("https".equalsIgnoreCase(request.getUrl().getScheme())) browser.loadUrl(request.getUrl().toString());
                        child.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        browser.loadUrl("https://smolish.com/search");
        handler.postDelayed(poll, 1500);
    }

    private void checkSession() {
        if (checking || finished || browser == null || browser.getUrl() == null) return;
        android.net.Uri uri = android.net.Uri.parse(browser.getUrl());
        String host = uri.getHost();
        if (host == null || !(host.equals("smolish.com") || host.endsWith(".smolish.com"))) return;
        checking = true;
        String script = "(function(){fetch('/api/profile',{credentials:'include',cache:'no-store'})" +
                ".then(async r=>{if(!r.ok)throw Error('Profile returned '+r.status);" +
                "let profile=await r.json();" +
                "if(!profile || !(profile.id||profile.userId||profile.handle))throw Error('Profile not ready');" +
                "SmolishAndroidBridge.onSession(JSON.stringify({profile:profile,userAgent:navigator.userAgent}));" +
                "}).catch(e=>SmolishAndroidBridge.onWaiting(String(e)));})();";
        browser.evaluateJavascript(script, null);
        handler.postDelayed(() -> checking = false, 5000);
    }

    private final class BrowserBridge {
        @JavascriptInterface public void onWaiting(String reason) {
            handler.post(() -> {
                checking = false;
                if (!finished && !transferring && transferError == null)
                    status.setText("Waiting for Smolish sign-in… " + reason);
            });
        }
        @JavascriptInterface public void onSession(String envelopeJson) {
            handler.post(() -> {
                if (finished || browser == null) return;
                String host = android.net.Uri.parse(browser.getUrl()).getHost();
                if (host == null || !(host.equals("smolish.com") || host.endsWith(".smolish.com"))) return;
                try {
                    JSONObject envelope = new JSONObject(envelopeJson);
                    JSONObject profile = Json.obj(envelope, "profile");
                    String identity = Json.first(profile, "id", "userId", "handle");
                    if (identity.isEmpty()) return;
                    if (addAccount) {
                        if (initialIdentity == null) { initialIdentity = identity; checking = false; return; }
                        if (initialIdentity.equalsIgnoreCase(identity)) {
                            status.setText("Use the website account switcher to add a different account.");
                            checking = false; return;
                        }
                    }
                    transferBrowserSession(profile, Json.str(envelope, "userAgent"), false);
                } catch (Exception e) { status.setText("Could not transfer session: " + e.getMessage()); }
                finally { checking = false; }
            });
        }
    }

    private void transferBrowserSession(JSONObject webProfile, String webUserAgent, boolean userInitiated) {
        if (finished || transferring || browser == null || browser.getUrl() == null) return;
        String host = android.net.Uri.parse(browser.getUrl()).getHost();
        if (host == null || !(host.equals("smolish.com") || host.endsWith(".smolish.com"))) {
            status.setText("Return to the Smolish page before transferring the session.");
            return;
        }
        String cookie = browserCookies();
        if (cookie.isEmpty()) { status.setText("No Smolish browser cookies yet. Finish sign-in and try again."); return; }
        if (!userInitiated && cookie.equals(failedCookie) && System.currentTimeMillis() - failedAt < 30_000)
            return;
        String agent = webUserAgent == null || webUserAgent.isEmpty()
                ? browser.getSettings().getUserAgentString() : webUserAgent;
        transferring = true;
        transferError = null;
        status.setText("Verifying your Smolish session…");
        new Thread(() -> {
            try {
                session.save(cookie, agent);
                JSONObject verified = new Api(session).get("api/profile", true);
                String actual = Json.first(verified, "id", "userId", "handle");
                if (actual.isEmpty()) throw new IllegalStateException("Smolish returned no profile.");
                if (webProfile != null) {
                    String expectedId = Json.first(webProfile, "id", "userId");
                    String actualId = Json.first(verified, "id", "userId");
                    String expectedHandle = Json.str(webProfile, "handle");
                    String actualHandle = Json.str(verified, "handle");
                    boolean mismatched = !expectedId.isEmpty() && !actualId.isEmpty()
                            ? !expectedId.equalsIgnoreCase(actualId)
                            : !expectedHandle.isEmpty() && !actualHandle.isEmpty()
                            && !expectedHandle.equalsIgnoreCase(actualHandle);
                    if (mismatched)
                        throw new IllegalStateException("The browser and app returned different accounts.");
                }
                handler.post(() -> {
                    if (finished) { session.clearNative(); return; }
                    CookieManager.getInstance().flush();
                    finished = true;
                    transferError = null;
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception error) {
                session.clearNative();
                handler.post(() -> {
                    transferring = false;
                    failedCookie = cookie;
                    failedAt = System.currentTimeMillis();
                    transferError = "Browser is signed in, but the app could not verify the session: " +
                            error.getMessage();
                    status.setText(transferError);
                });
            }
        }, "SmolishSessionTransfer").start();
    }

    private String browserCookies() {
        LinkedHashMap<String, String> pairs = new LinkedHashMap<>();
        String[] headers = {CookieManager.getInstance().getCookie("https://smolish.com/"),
                CookieManager.getInstance().getCookie(browser.getUrl())};
        for (String header : headers) {
            if (header == null) continue;
            for (String raw : header.split(";")) {
                String part = raw.trim();
                int equal = part.indexOf('=');
                if (equal > 0) pairs.put(part.substring(0, equal), part.substring(equal + 1));
            }
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    @Override public void onBackPressed() { if (browser != null && browser.canGoBack()) browser.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        finished = true;
        handler.removeCallbacks(poll);
        if (browser != null) { browser.removeJavascriptInterface("SmolishAndroidBridge"); browser.destroy(); }
        super.onDestroy();
    }
}
