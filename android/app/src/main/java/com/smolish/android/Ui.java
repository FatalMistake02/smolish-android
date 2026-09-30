package com.smolish.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

final class Ui {
    static final int BG = Color.rgb(10, 10, 10);
    static final int CARD = Color.rgb(24, 24, 24);
    static final int BLUE = Color.rgb(29, 161, 242);
    static final int TEXT = Color.WHITE;
    static final int SUB = Color.rgb(160, 160, 168);
    static final int BORDER = Color.rgb(49, 49, 52);
    private Ui() {}

    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout vertical(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(1); v.setBackgroundColor(BG); return v; }
    static LinearLayout horizontal(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static TextView text(Context c, String value, int sp, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextColor(TEXT); t.setTextSize(sp);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    static TextView sub(Context c, String value) { TextView t = text(c, value, 13, false); t.setTextColor(SUB); return t; }
    static Button button(Context c, String label, View.OnClickListener listener) {
        return styledButton(c, label, CARD, TEXT, listener);
    }
    static Button primary(Context c, String label, View.OnClickListener listener) {
        return styledButton(c, label, BLUE, Color.WHITE, listener);
    }
    private static Button styledButton(Context c, String label, int fill, int ink, View.OnClickListener listener) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextColor(ink);
        b.setTextSize(14); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinHeight(dp(c, 44)); b.setMinimumHeight(dp(c, 44));
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(c, fill, 12), null));
        b.setStateListAnimator(null);
        b.setOnClickListener(listener); return b;
    }
    static EditText edit(Context c, String hint, String value) {
        EditText e = new EditText(c); e.setHint(hint); e.setHintTextColor(SUB); e.setTextColor(TEXT);
        e.setText(value); e.setSingleLine(false);
        GradientDrawable shape = round(c, CARD, 12); shape.setStroke(dp(c, 1), BORDER);
        e.setBackground(shape);
        e.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9)); return e;
    }
    static GradientDrawable round(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static GradientDrawable surface(Context c, int radius) {
        GradientDrawable d = round(c, CARD, radius);
        d.setStroke(dp(c, 1), BORDER);
        return d;
    }
    static void pad(View v, int p) { v.setPadding(dp(v.getContext(), p), dp(v.getContext(), p), dp(v.getContext(), p), dp(v.getContext(), p)); }
    static void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(parent.getContext(), 8); parent.addView(child, p);
    }
    static ScrollView scroll(Activity a, LinearLayout body) {
        ScrollView s = new ScrollView(a); s.setFillViewport(true); s.addView(body); return s;
    }
    static boolean nearEnd(ScrollView scroll) {
        View content = scroll.getChildAt(0);
        return content != null && content.getHeight() > 0 &&
                content.getHeight() - scroll.getScrollY() - scroll.getHeight() < dp(scroll.getContext(), 220);
    }
    static void autoLoad(ScrollView scroll, View content, Runnable load) {
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (nearEnd(scroll)) load.run();
        });
        content.addOnLayoutChangeListener((view, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom != oldBottom) scroll.post(() -> { if (nearEnd(scroll)) load.run(); });
        });
    }
    static View videoTile(MainActivity host, JSONObject video, boolean studio, View.OnClickListener open) {
        LinearLayout tile = vertical(host);
        tile.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout art = new FrameLayout(host);
        art.setBackground(round(host, CARD, 13));
        art.setClipToOutline(true);
        ImageView image = Images.load(host, Json.first(video, "thumbnail", "thumbnailUrl"), 160, 206);
        art.addView(image, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(host);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, 0xDD000000}));
        art.addView(shade, new FrameLayout.LayoutParams(-1, dp(host, 65), Gravity.BOTTOM));
        TextView counts = text(host, "▶ " + Json.integer(video, "viewsCount") +
                "   ♥ " + Json.integer(video, "likesCount"), 12, true);
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        countParams.setMargins(dp(host, 9), 0, dp(host, 6), dp(host, 9));
        art.addView(counts, countParams);
        tile.addView(art, new LinearLayout.LayoutParams(-1, dp(host, 206)));
        String title = Json.first(video, "title", "description");
        if (title.isEmpty()) title = "Untitled video";
        TextView label = text(host, title, 13, true);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(host, 6);
        tile.addView(label, titleParams);
        if (studio) {
            TextView status = sub(host, Json.first(video, "status", "visibility"));
            tile.addView(status);
        }
        tile.setOnClickListener(open);
        tile.setContentDescription(title + ", " + Json.integer(video, "viewsCount") +
                " views, " + Json.integer(video, "likesCount") + " likes");
        return tile;
    }
    static LinearLayout emptyState(Context c, String symbol, String title, String detail) {
        LinearLayout card = vertical(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(surface(c, 18));
        pad(card, 24);
        TextView glyph = text(c, symbol, 36, false);
        glyph.setTextColor(BLUE); glyph.setGravity(Gravity.CENTER);
        add(card, glyph);
        TextView heading = text(c, title, 20, true);
        heading.setGravity(Gravity.CENTER); add(card, heading);
        TextView body = sub(c, detail);
        body.setGravity(Gravity.CENTER); add(card, body);
        return card;
    }
}
