package com.smolish.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;

final class StudioScreen {
    private final MainActivity host;
    private final ArrayList<JSONObject> videos = new ArrayList<>();
    private LinearLayout metrics;
    private LinearLayout list;
    private LinearLayout chartHost;
    private ScrollView contentScroll;
    private int days = 30;
    private int page = 1;
    private boolean loading;
    private int total;
    private int metricCount;
    private LinearLayout metricRow;

    StudioScreen(MainActivity host) { this.host = host; }

    void show() {
        LinearLayout body = Ui.vertical(host); Ui.pad(body, 14);
        if (!host.session.signedIn()) {
            Ui.add(body, Ui.emptyState(host, "▦", "Create with Studio",
                    "Connect your account to see analytics, manage your videos, and publish new work."));
            Ui.add(body, Ui.primary(host, "Go to Profile", v -> host.navigate("Profile")));
            host.setScreen(body); return;
        }
        Ui.add(body, Ui.primary(host, "Upload video", v -> host.pickVideo()));
        Ui.add(body, Ui.button(host, "Open web Studio", v -> host.openWeb("https://smolish.com/studio")));
        LinearLayout ranges = Ui.horizontal(host);
        for (int choice : new int[]{7, 30, 90}) {
            Button button = Ui.button(host, choice + "D", v -> { days = choice; loadAnalytics(); });
            ranges.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        Ui.add(body, ranges);
        metrics = Ui.vertical(host); Ui.add(body, metrics);
        chartHost = Ui.vertical(host); Ui.add(body, chartHost);
        Ui.add(body, Ui.text(host, "Your videos", 20, true));
        list = Ui.vertical(host); Ui.add(body, list);
        Ui.add(body, Ui.button(host, "Refresh", v -> { loadVideos(true); loadAnalytics(); }));
        contentScroll = Ui.scroll(host, body);
        Ui.autoLoad(contentScroll, list, () -> loadVideos(false));
        host.setScreen(contentScroll);
        loadAnalytics(); loadVideos(true);
    }

    private void loadVideos(boolean reset) {
        if (loading) return;
        if (!reset && (total == 0 || videos.size() >= total)) return;
        loading = true;
        int requested = reset ? 1 : page + 1;
        host.work(() -> host.api.get("api/videos", true, "page", String.valueOf(requested),
                "limit", "10", "sort", "date", "dir", "desc"), response -> {
            loading = false;
            if (reset) { videos.clear(); list.removeAllViews(); }
            page = requested;
            total = Json.integer(response, "total");
            HashSet<String> seen = new HashSet<>();
            for (JSONObject video : videos) seen.add(Json.str(video, "id"));
            JSONArray items = Json.array(response, "items", "videos");
            for (int i = 0; i < items.length(); i++) {
                JSONObject video = Json.at(items, i);
                if (seen.add(Json.str(video, "id"))) videos.add(video);
            }
            renderVideos();
        }, e -> { loading = false; host.error(e); });
    }

    private void renderVideos() {
        list.removeAllViews();
        if (videos.isEmpty()) Ui.add(list, Ui.sub(host, "No Studio videos yet."));
        LinearLayout row = null;
        for (int i = 0; i < videos.size(); i++) {
            JSONObject video = videos.get(i);
            if (i % 2 == 0) {
                row = Ui.horizontal(host);
                row.setGravity(android.view.Gravity.TOP);
                Ui.add(list, row);
            }
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i % 2 == 1) tileParams.leftMargin = Ui.dp(host, 10);
            row.addView(Ui.videoTile(host, video, true, v -> editVideo(video)), tileParams);
            if (i == videos.size() - 1 && i % 2 == 0)
                row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
        }
        if (contentScroll != null && total > videos.size())
            contentScroll.post(() -> { if (Ui.nearEnd(contentScroll)) loadVideos(false); });
        if (metrics != null && metricCount == 0 && !videos.isEmpty()) {
            int views = 0, likes = 0, comments = 0;
            for (JSONObject video : videos) {
                views += Json.integer(video, "viewsCount");
                likes += Json.integer(video, "likesCount");
                comments += Json.integer(video, "commentsCount");
            }
            metric(Json.make("views", views), "Views", "views");
            metric(Json.make("likes", likes), "Likes", "likes");
            metric(Json.make("comments", comments), "Comments", "comments");
        }
    }

    private void loadAnalytics() {
        if (metrics == null) return;
        metrics.removeAllViews(); Ui.add(metrics, Ui.sub(host, "Loading analytics…"));
        host.work(() -> host.api.get("api/studio/analytics", true, "days", String.valueOf(days)), response -> {
            metrics.removeAllViews(); chartHost.removeAllViews();
            metricCount = 0; metricRow = null;
            JSONObject summary = response.optJSONObject("summary");
            if (summary == null) summary = response.optJSONObject("totals");
            if (summary == null) summary = response;
            metric(summary, "Views", "views", "totalViews");
            metric(summary, "Unique viewers", "uniqueViewers", "viewers");
            metric(summary, "Watch time", "watchTimeSeconds", "totalWatchTimeSeconds", "watchTime");
            metric(summary, "Avg. watch time", "averageWatchTimeSeconds", "avgWatchTimeSeconds", "averageWatchTime");
            metric(summary, "Completion rate", "completionRate", "averageCompletionRate");
            metric(summary, "Likes", "likes", "likesCount");
            metric(summary, "Comments", "comments", "commentsCount");
            metric(summary, "Shares", "shares", "sharesCount");
            metric(summary, "Followers gained", "followersGained", "newFollowers");
            JSONArray points = Json.array(response, "daily", "series", "timeline");
            if (points.length() > 0) {
                Ui.add(chartHost, Ui.text(host, "Views over time", 18, true));
                Chart chart = new Chart(host, points);
                chartHost.addView(chart, new LinearLayout.LayoutParams(-1, Ui.dp(host, 190)));
            }
        }, e -> { metrics.removeAllViews(); Ui.add(metrics, Ui.sub(host, e.getMessage())); });
    }

    private void metric(JSONObject summary, String title, String... keys) {
        for (String key : keys) if (summary.has(key) && !summary.isNull(key)) {
            String value = String.valueOf(summary.opt(key));
            if (metricCount % 2 == 0) {
                metricRow = Ui.horizontal(host);
                Ui.add(metrics, metricRow);
            }
            LinearLayout card = Ui.vertical(host);
            card.setBackground(Ui.round(host, Ui.CARD, 16));
            Ui.pad(card, 12);
            Ui.add(card, Ui.sub(host, title));
            Ui.add(card, Ui.text(host, value, 23, true));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.rightMargin = Ui.dp(host, 8);
            metricRow.addView(card, params);
            metricCount++;
            return;
        }
    }

    private static final class Chart extends View {
        final float[] values;
        final Paint paint = new Paint(3);
        Chart(MainActivity host, JSONArray points) {
            super(host);
            values = new float[points.length()];
            for (int i = 0; i < values.length; i++) {
                JSONObject item = Json.at(points, i);
                values[i] = (float) item.optDouble("views", item.optDouble("count", 0));
            }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Ui.CARD); paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 18, 18, paint);
            if (values.length < 2) return;
            float max = 1;
            for (float v : values) max = Math.max(max, v);
            paint.setColor(Ui.BLUE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5);
            float pad = 18;
            for (int i = 1; i < values.length; i++) {
                float x1 = pad + (getWidth() - 2 * pad) * (i - 1) / (values.length - 1);
                float x2 = pad + (getWidth() - 2 * pad) * i / (values.length - 1);
                float y1 = getHeight() - pad - (getHeight() - 2 * pad) * values[i - 1] / max;
                float y2 = getHeight() - pad - (getHeight() - 2 * pad) * values[i] / max;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
    }

    private void editVideo(JSONObject video) {
        LinearLayout form = Ui.vertical(host); Ui.pad(form, 16);
        EditText title = Ui.edit(host, "Title", Json.str(video, "title"));
        EditText description = Ui.edit(host, "Description", Json.str(video, "description"));
        Spinner visibility = visibilitySpinner(Json.str(video, "visibility"));
        CheckBox flashing = checkbox("Flashing content warning", Json.bool(video, "epilepsyWarning"));
        CheckBox ai = checkbox("AI-generated", Json.bool(video, "aiGenerated"));
        Ui.add(form, title); Ui.add(form, description); Ui.add(form, visibility);
        Ui.add(form, flashing); Ui.add(form, ai);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle("Edit video").setView(form)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("Title required"); return; }
            String newTitle = title.getText().toString().trim();
            String newDescription = description.getText().toString();
            String newVisibility = visibility.getSelectedItem().toString().toLowerCase();
            boolean warning = flashing.isChecked();
            boolean generated = ai.isChecked();
            host.work(() -> host.api.send("PATCH", "api/videos/" + Json.str(video, "id"),
                    Json.make("title", newTitle, "description", newDescription,
                            "visibility", newVisibility,
                            "epilepsyWarning", warning, "aiGenerated", generated)),
                    result -> { dialog.dismiss(); loadVideos(true); });
        }));
        dialog.show();
    }

    private Spinner visibilitySpinner(String selected) {
        Spinner spinner = new Spinner(host);
        String[] choices = {"Private", "Unlisted", "Public"};
        spinner.setAdapter(new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, choices));
        int index = "public".equalsIgnoreCase(selected) ? 2 : "unlisted".equalsIgnoreCase(selected) ? 1 : 0;
        spinner.setSelection(index);
        return spinner;
    }
    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(host); box.setText(label); box.setTextColor(Ui.TEXT); box.setChecked(checked); return box;
    }

    void onVideoSelected(Uri file) {
        String name = "video.mp4"; long size = -1;
        try (Cursor cursor = host.getContentResolver().query(file, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        }
        if (size <= 0) { host.error(new IllegalArgumentException("The selected video did not provide a file size.")); return; }
        final String filename = name;
        final long length = size;
        Dialog dialog = new Dialog(host);
        LinearLayout form = Ui.vertical(host); Ui.pad(form, 16);
        IconView back = new IconView(host, "back");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> dialog.dismiss());
        form.addView(back, new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
        Ui.add(form, Ui.sub(host, filename + "  •  " + length + " bytes"));
        EditText title = Ui.edit(host, "Title", filename.replaceFirst("\\.[^.]+$", ""));
        EditText description = Ui.edit(host, "Description", "");
        Spinner visibility = visibilitySpinner("private");
        CheckBox flashing = checkbox("Flashing content warning", false);
        CheckBox ai = checkbox("AI-generated", false);
        Ui.add(form, title); Ui.add(form, description); Ui.add(form, visibility);
        Ui.add(form, flashing); Ui.add(form, ai);
        ProgressBar progress = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); Ui.add(form, progress);
        TextView status = Ui.sub(host, "Ready to upload"); Ui.add(form, status);
        Ui.add(form, Ui.primary(host, "Upload and save", v -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("Title required"); return; }
            v.setEnabled(false); dialog.setCancelable(false);
            String mime = host.getContentResolver().getType(file);
            if (mime == null) mime = "video/mp4";
            final String contentType = mime;
            final String wanted = visibility.getSelectedItem().toString().toLowerCase();
            final String uploadTitle = title.getText().toString().trim();
            final String uploadDescription = description.getText().toString();
            final boolean warning = flashing.isChecked();
            final boolean generated = ai.isChecked();
            host.work(() -> {
                JSONObject creation = host.api.send("POST", "api/videos", Json.make(
                        "filename", filename, "contentType", contentType, "sizeBytes", length));
                JSONObject video = Json.obj(creation, "video");
                String id = Json.str(video, "id");
                int partSize = Json.integer(creation, "partSize");
                int partCount = Json.integer(creation, "partCount");
                if (id.isEmpty() || partSize <= 0 || partCount <= 0)
                    throw new IllegalStateException("Smolish returned invalid upload details.");
                try (InputStream stream = host.getContentResolver().openInputStream(file)) {
                    if (stream == null) throw new IllegalStateException("Could not open selected video.");
                    for (int part = 1; part <= partCount; part++) {
                        byte[] buffer = new byte[partSize];
                        int count = 0;
                        while (count < partSize) {
                            int n = stream.read(buffer, count, partSize - count);
                            if (n < 0) break;
                            count += n;
                        }
                        if (count == 0) throw new IllegalStateException("The video ended before upload finished.");
                        byte[] actual = new byte[count];
                        System.arraycopy(buffer, 0, actual, 0, count);
                        JSONObject destination = host.api.send("POST", "api/videos/" + id + "/parts",
                                Json.make("partNumber", part));
                        String etag = host.api.putPart(Json.str(destination, "url"), actual);
                        host.api.send("PUT", "api/videos/" + id + "/parts",
                                Json.make("partNumber", part, "etag", etag, "sizeBytes", count));
                        int percent = Math.round(90f * part / partCount);
                        host.main.post(() -> { progress.setProgress(percent); status.setText("Uploading " + percent + "%"); });
                    }
                }
                host.api.send("POST", "api/videos/" + id + "/complete", new JSONObject());
                JSONObject update = Json.make("title", uploadTitle,
                        "description", uploadDescription, "visibility", "private",
                        "epilepsyWarning", warning, "aiGenerated", generated);
                host.api.send("PATCH", "api/videos/" + id, update);
                if (!"private".equals(wanted)) {
                    Thread.sleep(1000);
                    update.put("visibility", wanted);
                    host.api.send("PATCH", "api/videos/" + id, update);
                }
                return id;
            }, id -> { progress.setProgress(100); dialog.dismiss(); host.message("Video uploaded"); loadVideos(true); },
                    e -> { v.setEnabled(true); dialog.setCancelable(true); status.setText(e.getMessage()); host.error(e); });
        }));
        dialog.setContentView(Ui.scroll(host, form));
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }
}
