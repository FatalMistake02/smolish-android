package com.smolish.android;

import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class Api {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).build();
    private final SecureSession session;

    Api(SecureSession session) { this.session = session; }

    JSONObject get(String path, boolean auth, String... query) throws Exception {
        HttpUrl.Builder url = base(path).newBuilder();
        for (int i = 0; i + 1 < query.length; i += 2)
            if (query[i + 1] != null && !query[i + 1].isEmpty()) url.addQueryParameter(query[i], query[i + 1]);
        return request("GET", url.build(), null, auth, true);
    }

    JSONObject send(String method, String path, JSONObject body) throws Exception {
        return request(method, base(path), RequestBody.create(body.toString(), JSON), true, false);
    }

    String putPart(String signedUrl, byte[] data) throws Exception {
        HttpUrl url = HttpUrl.parse(signedUrl);
        if (url == null || !"https".equals(url.scheme())) throw new IOException("Invalid upload URL.");
        Request request = new Request.Builder().url(url)
                .put(RequestBody.create(data, MediaType.get("application/octet-stream"))).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Upload part failed (" + response.code() + ").");
            String etag = response.header("ETag");
            if (etag == null || etag.isEmpty()) throw new IOException("Upload response did not include an ETag.");
            return etag;
        }
    }

    private HttpUrl base(String path) {
        if (path.startsWith("/") || path.contains("..") || path.contains("?")) throw new IllegalArgumentException("Invalid API path.");
        HttpUrl root = HttpUrl.get("https://smolish.com/");
        return root.newBuilder().addPathSegments(path).build();
    }

    private JSONObject request(String method, HttpUrl url, RequestBody body, boolean authRequired, boolean optionalAuth) throws Exception {
        String cookie = session.cookie();
        if (authRequired && cookie.isEmpty()) throw new IOException("Sign in from Profile first.");
        Request.Builder request = new Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache");
        if (!cookie.isEmpty() && (authRequired || optionalAuth)) {
            request.header("Cookie", cookie).header("Origin", "https://smolish.com")
                    .header("Referer", "https://smolish.com/")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Accept-Language", "en-US,en;q=0.9");
            if (!session.userAgent().isEmpty()) request.header("User-Agent", session.userAgent());
        }
        request.method(method, body);
        try (Response response = client.newCall(request.build()).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = "Smolish request failed (" + response.code() + ").";
                try {
                    JSONObject error = new JSONObject(text);
                    message = Json.first(error, "error", "message");
                    if (message.isEmpty()) message = "Smolish request failed (" + response.code() + ").";
                } catch (Exception ignored) {}
                throw new IOException(message);
            }
            for (String setCookie : response.headers("Set-Cookie"))
                CookieManager.getInstance().setCookie("https://smolish.com", setCookie);
            if (!response.headers("Set-Cookie").isEmpty()) CookieManager.getInstance().flush();
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        }
    }
}
