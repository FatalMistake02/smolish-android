package com.smolish.android;

import android.app.Dialog;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

final class FeedScreen {
    private final MainActivity host;
    private final ArrayList<JSONObject> videos = new ArrayList<>();
    private final HashSet<String> usedCursors = new HashSet<>();
    private String cursor;
    private int index;
    private boolean loading;
    private FrameLayout viewport;
    private ViewPager2 pager;
    private VideoAdapter adapter;
    private TextView emptyMessage;
    private VideoView visiblePlayer;
    private boolean active = true;
    private final ViewPager2.OnPageChangeCallback pageChange = new ViewPager2.OnPageChangeCallback() {
        @Override public void onPageSelected(int position) {
            if (!active) return;
            index = position;
            if (position >= videos.size()) {
                deactivatePlayer();
                if (cursor != null) load(false);
                return;
            }
            pager.post(() -> activatePage(position));
            if (cursor != null && position >= videos.size() - 4) load(false);
        }
        @Override public void onPageScrollStateChanged(int state) {
            if (state == ViewPager2.SCROLL_STATE_IDLE && active) {
                if (index < videos.size()) pager.post(() -> activatePage(index));
                else if (cursor != null) load(false);
            }
        }
    };

    FeedScreen(MainActivity host) { this.host = host; }

    void dispose() {
        active = false;
        if (pager != null) pager.unregisterOnPageChangeCallback(pageChange);
        deactivatePlayer();
    }

    void show() {
        viewport = new FrameLayout(host);
        viewport.setBackgroundColor(Color.BLACK);
        pager = new ViewPager2(host);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new VideoAdapter();
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(pageChange);
        viewport.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        emptyMessage = Ui.text(host, "Loading videos…", 16, false);
        emptyMessage.setGravity(Gravity.CENTER);
        viewport.addView(emptyMessage, new FrameLayout.LayoutParams(-1, -1));
        host.setScreen(viewport);
        load(true);
    }

    private void load(boolean reset) {
        if (!active || loading) return;
        if (!reset && (cursor == null || !usedCursors.add(cursor))) return;
        loading = true;
        if (reset) {
            videos.clear(); usedCursors.clear(); cursor = null; index = 0;
            deactivatePlayer();
            adapter.notifyDataSetChanged();
            emptyMessage.setText("Loading videos…");
            emptyMessage.setVisibility(View.VISIBLE);
        }
        String next = cursor;
        host.work(() -> host.api.get("api/feed", false, "cursor", next,
                "_refresh", reset ? String.valueOf(System.currentTimeMillis()) : null), response -> {
            if (!active) return;
            loading = false;
            HashSet<String> ids = new HashSet<>();
            for (JSONObject video : videos) ids.add(Json.str(video, "id"));
            JSONArray items = Json.array(response, "items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject video = Json.at(items, i);
                if (ids.add(Json.str(video, "id"))) videos.add(video);
            }
            String newCursor = Json.first(response, "nextCursor");
            cursor = newCursor.isEmpty() || usedCursors.contains(newCursor) ? null : newCursor;
            adapter.notifyDataSetChanged();
            if (videos.isEmpty()) {
                emptyMessage.setText("No videos found. Try refreshing later.");
                emptyMessage.setVisibility(View.VISIBLE);
            } else {
                emptyMessage.setVisibility(View.GONE);
                if (reset) pager.setCurrentItem(0, false);
                if (index < videos.size()) pager.post(() -> activatePage(index));
                if (cursor != null && index >= videos.size() - 4) load(false);
            }
        }, e -> {
            if (!active) return;
            loading = false;
            if (next != null) usedCursors.remove(next);
            if (videos.isEmpty()) {
                emptyMessage.setText("Couldn't load videos. Tap to try again.");
                emptyMessage.setOnClickListener(v -> load(true));
                emptyMessage.setVisibility(View.VISIBLE);
            } else host.message("Couldn't load more videos. Swipe again to retry.");
        });
    }

    void showVideoDialog(JSONObject video) {
        Dialog dialog = new Dialog(host, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.getWindow();
        deactivatePlayer();
        FrameLayout panel = videoPanel(video, dialog::dismiss);
        dialog.setContentView(panel);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.setOnDismissListener(d -> {
            host.releasePlayer();
            if (pager != null && pager.isAttachedToWindow() && index < videos.size())
                pager.post(() -> activatePage(index));
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
    }

    private void activatePage(int position) {
        if (!active || pager == null || position != pager.getCurrentItem() || position >= videos.size()) return;
        View child = pager.getChildAt(0);
        if (!(child instanceof RecyclerView)) return;
        RecyclerView.ViewHolder item = ((RecyclerView) child).findViewHolderForAdapterPosition(position);
        if (!(item instanceof PageHolder)) return;
        PageHolder holder = (PageHolder) item;
        if (holder.player == null || holder.video == null || holder.player == visiblePlayer) return;
        deactivatePlayer();
        visiblePlayer = holder.player;
        visiblePlayer.setTag(Boolean.TRUE);
        host.setPlayer(visiblePlayer);
        String source = Json.str(holder.video, "src");
        if (!source.isEmpty()) {
            visiblePlayer.setVisibility(View.VISIBLE);
            visiblePlayer.setVideoURI(Uri.parse(source));
            visiblePlayer.start();
        }
    }

    private void deactivatePlayer() {
        if (visiblePlayer == null) return;
        visiblePlayer.setTag(Boolean.FALSE);
        visiblePlayer.setVisibility(View.INVISIBLE);
        host.releasePlayer(visiblePlayer);
        visiblePlayer = null;
    }

    private final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        VideoView player;
        JSONObject video;
        PageHolder(FrameLayout container) { super(container); this.container = container; }
    }

    private final class VideoAdapter extends RecyclerView.Adapter<PageHolder> {
        @Override public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(host);
            container.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new PageHolder(container);
        }
        @Override public void onBindViewHolder(PageHolder holder, int position) {
            holder.container.removeAllViews();
            holder.player = null;
            holder.video = null;
            if (position >= videos.size()) {
                TextView waiting = Ui.text(host, loading ? "Loading next video…" : "Tap to retry", 15, false);
                waiting.setGravity(Gravity.CENTER);
                waiting.setOnClickListener(v -> load(false));
                holder.container.addView(waiting, new FrameLayout.LayoutParams(-1, -1));
                return;
            }
            holder.video = videos.get(position);
            FrameLayout panel = videoPanel(holder.video, null);
            holder.player = (VideoView) panel.getTag();
            holder.container.addView(panel, new FrameLayout.LayoutParams(-1, -1));
            if (pager != null && position == pager.getCurrentItem())
                pager.post(() -> activatePage(position));
        }
        @Override public void onViewRecycled(PageHolder holder) {
            if (holder.player == visiblePlayer) deactivatePlayer();
            holder.container.removeAllViews();
            holder.player = null;
            holder.video = null;
        }
        @Override public int getItemCount() { return videos.size() + (cursor == null ? 0 : 1); }
    }

    private FrameLayout videoPanel(JSONObject video, Runnable close) {
        FrameLayout frame = new FrameLayout(host);
        frame.setBackgroundColor(Color.BLACK);
        if (close != null) frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        String source = Json.str(video, "src");
        String thumbnail = Json.first(video, "thumbnail", "thumbnailUrl");
        if (!thumbnail.isEmpty()) frame.addView(Images.load(host, thumbnail, 320, 560),
                new FrameLayout.LayoutParams(-1, -1));
        VideoView player = new VideoView(host);
        player.setTag(close != null ? Boolean.TRUE : Boolean.FALSE);
        player.setVisibility(View.INVISIBLE);
        frame.setTag(player);
        final MediaPlayer[] media = {null};
        final boolean[] muted = {false};
        player.setOnPreparedListener(prepared -> {
            media[0] = prepared; prepared.setLooping(true);
            prepared.setVolume(muted[0] ? 0 : 1, muted[0] ? 0 : 1);
            if (Boolean.TRUE.equals(player.getTag())) {
                player.setVisibility(View.VISIBLE);
                player.start();
            }
        });
        frame.addView(player, new FrameLayout.LayoutParams(-1, -1));
        if (close != null && !source.isEmpty()) {
            host.setPlayer(player);
            player.setVisibility(View.VISIBLE);
            player.setVideoURI(Uri.parse(source));
            player.start();
        }
        View touch = new View(host);
        touch.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause(); else player.start();
        });
        frame.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(host);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, 0x22000000, 0xDD000000}));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(-1, Ui.dp(host, 270), Gravity.BOTTOM);
        frame.addView(shade, shadeParams);

        boolean[] following = {Json.bool(video, "viewerFollows")};
        LinearLayout info = Ui.vertical(host);
        info.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout creatorRow = Ui.horizontal(host);
        ImageView avatar = Images.loadCircle(host, Json.first(video, "authorAvatar", "avatarUrl"), 38);
        creatorRow.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(host, 38), Ui.dp(host, 38)));
        LinearLayout identity = Ui.vertical(host);
        identity.setBackgroundColor(Color.TRANSPARENT);
        TextView creatorName = Ui.text(host, Json.first(video, "authorName", "displayName"), 14, true);
        creatorName.setSingleLine(true);
        creatorName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(creatorName);
        identity.addView(Ui.sub(host, "@" + Json.str(video, "authorHandle")));
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.leftMargin = Ui.dp(host, 8);
        creatorRow.addView(identity, identityParams);
        View.OnClickListener openCreator = v -> host.showCreator(Json.make(
                "userId", Json.str(video, "authorId"), "handle", Json.str(video, "authorHandle"),
                "displayName", Json.str(video, "authorName"), "avatarUrl", Json.str(video, "authorAvatar"),
                "followersCount", Json.integer(video, "authorFollowers"), "braincells", Json.integer(video, "authorBraincells"),
                "viewerFollows", following[0]));
        avatar.setOnClickListener(openCreator);
        identity.setOnClickListener(openCreator);
        Button follow = Ui.primary(host, following[0] ? "Following" : "Follow", v -> {
            if (!host.requireAuth()) return;
            boolean next = !following[0]; following[0] = next;
            ((Button) v).setText(next ? "Following" : "Follow");
            v.setBackground(Ui.round(host, next ? 0xCC303034 : Ui.BLUE, 18));
            host.work(() -> host.api.send(next ? "POST" : "DELETE", "api/follow",
                    Json.make("userId", Json.str(video, "authorId"))), result -> {}, e -> {
                following[0] = !next; ((Button) v).setText(following[0] ? "Following" : "Follow");
                v.setBackground(Ui.round(host, following[0] ? 0xCC303034 : Ui.BLUE, 18));
                host.error(e);
            });
        });
        follow.setTextSize(11);
        follow.setBackground(Ui.round(host, following[0] ? 0xCC303034 : Ui.BLUE, 18));
        LinearLayout.LayoutParams followParams = new LinearLayout.LayoutParams(Ui.dp(host, 70), Ui.dp(host, 36));
        followParams.leftMargin = Ui.dp(host, 5);
        creatorRow.addView(follow, followParams);
        Ui.add(info, creatorRow);
        if (!Json.str(video, "title").isEmpty()) Ui.add(info, Ui.text(host, Json.str(video, "title"), 15, true));
        TextView description = Ui.text(host, Json.str(video, "description"), 13, false);
        description.setMaxLines(2);
        description.setOnClickListener(v -> description.setMaxLines(description.getMaxLines() == 2 ? 100 : 2));
        if (!Json.str(video, "description").isEmpty()) Ui.add(info, description);
        String flags = (Json.bool(video, "aiGenerated") ? "  • AI" : "") +
                (Json.bool(video, "epilepsyWarning") ? "  • Flashing content" : "");
        Ui.add(info, Ui.sub(host, Json.integer(video, "viewsCount") + " views" + flags));
        JSONArray likers = Json.array(video, "friendLikers");
        if (likers.length() > 0) Ui.add(info, Ui.sub(host, "Liked by " + Json.first(Json.at(likers, 0), "name", "handle")));
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                Math.max(Ui.dp(host, 180), host.getResources().getDisplayMetrics().widthPixels - Ui.dp(host, 86)),
                -2, Gravity.BOTTOM | Gravity.LEFT);
        infoParams.setMargins(Ui.dp(host, 12), 0, 0, Ui.dp(host, 12));
        frame.addView(info, infoParams);

        LinearLayout actions = Ui.vertical(host);
        actions.setBackgroundColor(Color.TRANSPARENT);
        actions.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean[] liked = {Json.bool(video, "viewerLiked")};
        boolean[] saved = {Json.bool(video, "viewerSaved")};
        ActionItem like = action("heart", String.valueOf(Json.integer(video, "likesCount")), v -> {
            if (!host.requireAuth()) return;
            boolean next = !liked[0]; liked[0] = next;
            likeState(v, next, video);
            host.work(() -> host.api.send(next ? "POST" : "DELETE", "api/likes",
                    Json.make("videoId", Json.str(video, "id"))), result -> {}, e -> {
                liked[0] = !next; likeState(v, liked[0], video); host.error(e);
            });
        });
        like.setAccent(liked[0]);
        Ui.add(actions, like.root);
        Ui.add(actions, action("comment", String.valueOf(Json.integer(video, "commentsCount")),
                v -> new SocialScreens(host).showComments(Json.str(video, "id"))).root);
        Ui.add(actions, action("share", "Share",
                v -> host.share("https://smolish.com/v/" + Json.str(video, "id"))).root);
        ActionItem bookmark = action("bookmark", saved[0] ? "Saved" : "Save", v -> {
            if (!host.requireAuth()) return;
            boolean next = !saved[0]; saved[0] = next;
            ActionItem item = (ActionItem) v.getTag();
            item.label.setText(next ? "Saved" : "Save"); item.setAccent(next);
            host.work(() -> host.api.send(next ? "POST" : "DELETE", "api/bookmarks",
                    Json.make("videoId", Json.str(video, "id"))), result -> {}, e -> {
                saved[0] = !next; item.label.setText(saved[0] ? "Saved" : "Save");
                item.setAccent(saved[0]); host.error(e);
            });
        });
        bookmark.setAccent(saved[0]);
        Ui.add(actions, bookmark.root);
        Ui.add(actions, action("more", "More", v -> new AlertDialog.Builder(host)
                .setItems(new String[]{muted[0] ? "Unmute" : "Mute", "Not interested", "Report"},
                        (d, which) -> {
                            if (which == 0) {
                                muted[0] = !muted[0];
                                if (media[0] != null) media[0].setVolume(muted[0] ? 0 : 1, muted[0] ? 0 : 1);
                            } else if (host.requireAuth()) host.openWeb("https://smolish.com/v/" + Json.str(video, "id"));
                        }).show()).root);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                Ui.dp(host, 58), -2, Gravity.RIGHT | Gravity.BOTTOM);
        actionParams.setMargins(0, 0, Ui.dp(host, 8), Ui.dp(host, 12));
        frame.addView(actions, actionParams);
        IconView top = new IconView(host, close == null ? "refresh" : "back");
        top.setBackground(Ui.round(host, 0xBF111111, 24));
        top.setContentDescription(close == null ? "Refresh feed" : "Back");
        top.setOnClickListener(v -> { if (close == null) load(true); else close.run(); });
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(Ui.dp(host, 44),
                Ui.dp(host, 44), Gravity.TOP | (close == null ? Gravity.RIGHT : Gravity.LEFT));
        topParams.setMargins(Ui.dp(host, 12), Ui.dp(host, 12), Ui.dp(host, 12), 0);
        frame.addView(top, topParams);
        return frame;
    }

    private void likeState(View view, boolean liked, JSONObject video) {
        ActionItem item = (ActionItem) view.getTag();
        int count = Json.integer(video, "likesCount") + (liked == Json.bool(video, "viewerLiked") ? 0 : liked ? 1 : -1);
        item.label.setText(String.valueOf(Math.max(0, count)));
        item.setAccent(liked);
    }

    private ActionItem action(String iconName, String caption, View.OnClickListener listener) {
        LinearLayout root = Ui.vertical(host);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        IconView icon = new IconView(host, iconName);
        icon.setBackground(Ui.round(host, 0xC41C1C1C, 24));
        root.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
        TextView label = Ui.text(host, caption, 11, true);
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        ActionItem item = new ActionItem(root, icon, label);
        root.setTag(item);
        root.setContentDescription(caption);
        root.setOnClickListener(listener);
        icon.setOnClickListener(v -> listener.onClick(root));
        return item;
    }

    private static final class ActionItem {
        final LinearLayout root;
        final IconView icon;
        final TextView label;
        ActionItem(LinearLayout root, IconView icon, TextView label) {
            this.root = root; this.icon = icon; this.label = label;
        }
        void setAccent(boolean selected) {
            icon.setInk(selected ? Ui.BLUE : Ui.TEXT);
            icon.setBackground(Ui.round(icon.getContext(), selected ? 0xDD173A52 : 0xC41C1C1C, 24));
        }
    }
}
