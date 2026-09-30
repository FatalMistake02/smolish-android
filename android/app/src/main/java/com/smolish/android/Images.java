package com.smolish.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Images {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount() / 1024; }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private Images() {}

    static ImageView loadCircle(MainActivity host, String address, int size) {
        ImageView image = load(host, address, size, size);
        image.setBackground(Ui.round(host, Ui.CARD, size / 2));
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        return image;
    }

    static ImageView load(MainActivity host, String address, int width, int height) {
        ImageView image = new ImageView(host);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Ui.round(host, Ui.CARD, 10));
        image.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Ui.dp(host, width), Ui.dp(host, height)));
        if (address == null || address.isEmpty()) return image;
        Bitmap cached = CACHE.get(address);
        if (cached != null) { image.setImageBitmap(cached); return image; }
        IO.execute(() -> {
            try {
                URL url = new URL(address);
                if (!"https".equalsIgnoreCase(url.getProtocol())) return;
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        CACHE.put(address, bitmap);
                        host.main.post(() -> image.setImageBitmap(bitmap));
                    }
                } finally { connection.disconnect(); }
            } catch (Exception ignored) {}
        });
        return image;
    }
}
