package com.smolish.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.PopupMenu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.time.Duration;
import java.time.Instant;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

final class SocialScreens {
    private final MainActivity host;
    SocialScreens(MainActivity host) { this.host = host; }

    void showSearch() {
        LinearLayout root = Ui.vertical(host); Ui.pad(root, 14);
        EditText query = Ui.edit(host, "Search creators", "");
        query.setSingleLine(true);
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout results = Ui.vertical(host);
        Ui.pad(results, 4);
        searchPlaceholder(results);
        Runnable search = () -> {
            String q = query.getText().toString().trim();
            if (q.isEmpty()) { searchPlaceholder(results); return; }
            results.removeAllViews(); Ui.add(results, Ui.sub(host, "Searching…"));
            host.work(() -> host.api.get("api/search", false, "tab", "users", "q", q), response -> {
                if (!q.equals(query.getText().toString().trim())) return;
                results.removeAllViews();
                JSONArray items = Json.array(response, "items");
                if (items.length() == 0) Ui.add(results, Ui.sub(host, "No creators found."));
                for (int i = 0; i < items.length(); i++) {
                    JSONObject creator = Json.at(items, i);
                    LinearLayout row = Ui.horizontal(host);
                    row.setBackground(Ui.surface(host, 14));
                    row.setPadding(Ui.dp(host, 12), Ui.dp(host, 10), Ui.dp(host, 12), Ui.dp(host, 10));
                    row.addView(Images.load(host, Json.str(creator, "avatarUrl"), 48, 48));
                    LinearLayout details = Ui.vertical(host);
                    details.setBackgroundColor(Color.TRANSPARENT);
                    Ui.add(details, Ui.text(host, Json.first(creator, "displayName", "handle"), 16, true));
                    Ui.add(details, Ui.sub(host, "@" + Json.str(creator, "handle") + "  ·  " +
                            Json.integer(creator, "followersCount") + " followers"));
                    LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
                    detailsParams.leftMargin = Ui.dp(host, 12);
                    row.addView(details, detailsParams);
                    TextView arrow = Ui.sub(host, "›");
                    arrow.setTextSize(24);
                    row.addView(arrow);
                    row.setOnClickListener(v -> showCreator(creator));
                    Ui.add(results, row);
                }
            }, host::error);
        };
        Ui.add(root, query);
        root.addView(Ui.scroll(host, results), new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] pending = {null};
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pending[0] != null) host.main.removeCallbacks(pending[0]);
                if (s.toString().trim().isEmpty()) { searchPlaceholder(results); return; }
                pending[0] = search;
                host.main.postDelayed(search, 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        query.setOnEditorActionListener((v, action, event) -> { search.run(); return true; });
        host.setScreen(root);
    }

    private void searchPlaceholder(LinearLayout results) {
        results.removeAllViews();
        TextView icon = Ui.text(host, "⌕", 30, false);
        icon.setTextColor(Ui.BLUE); icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(-1, -2);
        top.topMargin = Ui.dp(host, 96);
        results.addView(icon, top);
        TextView prompt = Ui.sub(host, "Find creators on Smolish");
        prompt.setGravity(Gravity.CENTER);
        Ui.add(results, prompt);
    }

    void showCreator(JSONObject creator) {
        Dialog dialog = fullDialog();
        LinearLayout body = Ui.vertical(host); Ui.pad(body, 16);
        LinearLayout top = Ui.horizontal(host);
        top.addView(backIcon(() -> dialog.dismiss()),
                new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
        top.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
        IconView creatorMenu = new IconView(host, "more");
        creatorMenu.setContentDescription("Creator menu");
        top.addView(creatorMenu, new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
        Ui.add(body, top);
        String handle = Json.str(creator, "handle");
        creatorMenu.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(host, creatorMenu);
            menu.getMenu().add("Share profile");
            menu.setOnMenuItemClickListener(item -> {
                host.share("https://smolish.com/@" + handle);
                return true;
            });
            menu.show();
        });
        body.addView(Images.loadCircle(host, Json.first(creator, "avatarUrl", "authorAvatar"), 76),
                new LinearLayout.LayoutParams(Ui.dp(host, 76), Ui.dp(host, 76)));
        Ui.add(body, Ui.text(host, Json.first(creator, "displayName", "authorName", "handle"), 24, true));
        Ui.add(body, Ui.sub(host, "@" + handle));
        if (!Json.str(creator, "bio").isEmpty()) Ui.add(body, Ui.text(host, Json.str(creator, "bio"), 15, false));
        Ui.add(body, Ui.sub(host, Json.integer(creator, "followersCount") + " followers" +
                (creator.has("videosCount") ? "   •   " + Json.integer(creator, "videosCount") + " videos" : "") +
                "   •   " + Json.integer(creator, "braincells") + " braincells"));
        boolean[] following = {Json.bool(creator, "viewerFollows")};
        Button follow = Ui.button(host, following[0] ? "Following" : "Follow", v -> {
            if (!host.requireAuth()) return;
            boolean next = !following[0]; following[0] = next;
            ((Button) v).setText(next ? "Following" : "Follow");
            host.work(() -> host.api.send(next ? "POST" : "DELETE", "api/follow",
                    Json.make("userId", Json.first(creator, "userId", "authorId"))), r -> {}, e -> {
                following[0] = !next; ((Button) v).setText(following[0] ? "Following" : "Follow"); host.error(e);
            });
        });
        Ui.add(body, follow);
        Ui.add(body, Ui.text(host, "Videos", 19, true));
        LinearLayout list = Ui.vertical(host);
        Ui.add(body, list);
        ScrollView scroll = Ui.scroll(host, body);
        dialog.setContentView(scroll);
        showFull(dialog);
        startCreatorVideos(handle, list, scroll);
    }

    private static final class VideoListState {
        String cursor;
        boolean loading;
        boolean done;
        int count;
        LinearLayout row;
        View filler;
        final HashSet<String> ids = new HashSet<>();
        final HashSet<String> cursors = new HashSet<>();
    }

    private void startCreatorVideos(String handle, LinearLayout list, ScrollView scroll) {
        VideoListState state = new VideoListState();
        Ui.autoLoad(scroll, list, () -> loadCreatorVideos(handle, list, scroll, state));
        loadCreatorVideos(handle, list, scroll, state);
    }

    private void loadCreatorVideos(String handle, LinearLayout list, ScrollView scroll, VideoListState state) {
        if (state.loading || state.done || !scroll.isAttachedToWindow()) return;
        String next = state.cursor;
        if (next != null && !state.cursors.add(next)) { state.done = true; return; }
        state.loading = true;
        host.work(() -> host.api.get("api/feed", false, "author", handle, "cursor", next), response -> {
            if (!scroll.isAttachedToWindow()) return;
            state.loading = false;
            if (next == null) { list.removeAllViews(); state.count = 0; state.row = null; state.filler = null; }
            JSONArray items = Json.array(response, "items");
            if (items.length() == 0 && next == null) Ui.add(list, Ui.sub(host, "No public videos."));
            for (int i = 0; i < items.length(); i++) {
                JSONObject video = Json.at(items, i);
                if (!state.ids.add(Json.str(video, "id"))) continue;
                if (state.count % 2 == 0) {
                    state.row = Ui.horizontal(host);
                    state.row.setGravity(Gravity.TOP);
                    Ui.add(list, state.row);
                } else if (state.filler != null) {
                    state.row.removeView(state.filler);
                    state.filler = null;
                }
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1);
                if (state.count % 2 == 1) tileParams.leftMargin = Ui.dp(host, 10);
                state.row.addView(Ui.videoTile(host, video, false, v -> host.showVideo(video)), tileParams);
                state.count++;
            }
            if (state.count % 2 == 1 && state.row != null && state.filler == null) {
                state.filler = new View(host);
                state.row.addView(state.filler, new LinearLayout.LayoutParams(0, 1, 1));
            }
            String returned = Json.str(response, "nextCursor");
            state.cursor = returned.isEmpty() ? null : returned;
            state.done = state.cursor == null || state.cursors.contains(state.cursor);
            if (!state.done) scroll.post(() -> { if (Ui.nearEnd(scroll)) loadCreatorVideos(handle, list, scroll, state); });
        }, e -> { state.loading = false; if (next != null) state.cursors.remove(next); host.error(e); });
    }

    void showProfile() {
        LinearLayout body = Ui.vertical(host); Ui.pad(body, 16);
        if (!host.session.signedIn()) {
            LinearLayout hero = Ui.vertical(host);
            hero.setGravity(Gravity.CENTER_HORIZONTAL);
            hero.setPadding(Ui.dp(host, 22), Ui.dp(host, 55), Ui.dp(host, 22), Ui.dp(host, 34));
            ImageView logo = new ImageView(host);
            logo.setImageResource(com.smolish.android.R.drawable.ic_smolish_foreground);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(Ui.dp(host, 48), Ui.dp(host, 48));
            logoParams.bottomMargin = Ui.dp(host, 12);
            hero.addView(logo, logoParams);
            TextView heading = Ui.text(host, "Your space on Smolish", 25, true);
            heading.setGravity(Gravity.CENTER);
            Ui.add(hero, heading);
            TextView detail = Ui.sub(host, "Save the videos you love, follow creators, and share your own work.");
            detail.setGravity(Gravity.CENTER);
            Ui.add(hero, detail);
            Ui.add(body, hero);
            Ui.add(body, Ui.primary(host, "Sign in", v -> host.webSignIn(false)));
            Button fallback = Ui.button(host, "Use browser session manually", v -> manualSignIn());
            fallback.setTextColor(Ui.SUB);
            Ui.add(body, fallback);
            host.setScreen(Ui.scroll(host, body));
            return;
        }
        LinearLayout profile = Ui.vertical(host);
        Ui.add(profile, Ui.sub(host, "Loading your profile…"));
        Ui.add(body, profile);
        ScrollView profileScroll = Ui.scroll(host, body);
        host.setScreen(profileScroll);
        host.work(() -> host.api.get("api/profile", true), result -> {
            profile.removeAllViews();
            String handle = Json.str(result, "handle");
            String bannerUrl = Json.first(result, "bannerUrl", "banner");
            String avatarUrl = Json.first(result, "avatar", "avatarUrl");
            host.setProfileAvatar(avatarUrl);
            if (!bannerUrl.isEmpty()) {
                ImageView banner = Images.load(host, bannerUrl, 320, 106);
                banner.setBackground(Ui.round(host, Ui.CARD, 16));
                banner.setClipToOutline(true);
                Ui.add(profile, banner);
            }
            LinearLayout identity = Ui.horizontal(host);
            ImageView avatar = Images.loadCircle(host, avatarUrl, 72);
            identity.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(host, 72), Ui.dp(host, 72)));
            LinearLayout names = Ui.vertical(host);
            names.setBackgroundColor(Color.TRANSPARENT);
            Ui.add(names, Ui.text(host, Json.first(result, "displayName", "name", "handle"), 23, true));
            Ui.add(names, Ui.sub(host, "@" + handle));
            LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(0, -2, 1);
            namesParams.leftMargin = Ui.dp(host, 14);
            identity.addView(names, namesParams);
            IconView accounts = new IconView(host, "more");
            accounts.setContentDescription("Profile menu");
            accounts.setBackground(Ui.round(host, Ui.CARD, 22));
            accounts.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(host, accounts);
                menu.getMenu().add("Share profile");
                menu.getMenu().add("Switch account");
                menu.getMenu().add("Sign out");
                menu.setOnMenuItemClickListener(item -> {
                    String choice = item.getTitle().toString();
                    if (choice.equals("Share profile")) host.share("https://smolish.com/@" + handle);
                    else if (choice.equals("Switch account")) showAccounts();
                    else if (choice.equals("Sign out")) confirmSignOut();
                    return true;
                });
                menu.show();
            });
            identity.addView(accounts, new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
            Ui.add(profile, identity);
            if (!Json.str(result, "bio").isEmpty()) Ui.add(profile, Ui.text(host, Json.str(result, "bio"), 14, false));
            LinearLayout stats = Ui.horizontal(host);
            addProfileStat(stats, Json.integer(result, "followersCount"), "Followers");
            addProfileStat(stats, Json.integer(result, "followingCount"), "Following");
            addProfileStat(stats, Json.integer(result, "braincells"), "Braincells");
            Ui.add(profile, stats);
            Ui.add(profile, Ui.text(host, "Public videos", 19, true));
            LinearLayout list = Ui.vertical(host); Ui.add(profile, list);
            if (!handle.isEmpty()) startCreatorVideos(handle, list, profileScroll);
        }, e -> {
            profile.removeAllViews();
            Ui.add(profile, Ui.emptyState(host, "⌁", "Profile unavailable", e.getMessage()));
            Ui.add(profile, Ui.button(host, "Try again", v -> showProfile()));
        });
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(host).setTitle("Sign out?")
                .setPositiveButton("Sign out", (dialog, which) -> {
                    host.session.clear(); host.refreshProfileNavigation(); showProfile();
                }).setNegativeButton("Cancel", null).show();
    }

    private void addProfileStat(LinearLayout row, int count, String label) {
        LinearLayout stat = Ui.vertical(host);
        stat.setBackground(Ui.surface(host, 14));
        stat.setGravity(Gravity.CENTER);
        stat.setPadding(Ui.dp(host, 4), Ui.dp(host, 12), Ui.dp(host, 4), Ui.dp(host, 12));
        TextView number = Ui.text(host, String.valueOf(count), 18, true);
        number.setGravity(Gravity.CENTER);
        stat.addView(number);
        TextView caption = Ui.sub(host, label);
        caption.setTextSize(11);
        caption.setGravity(Gravity.CENTER);
        stat.addView(caption);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) params.leftMargin = Ui.dp(host, 8);
        row.addView(stat, params);
    }

    private void manualSignIn() {
        LinearLayout form = Ui.vertical(host); Ui.pad(form, 16);
        EditText cookie = Ui.edit(host, "Complete Cookie header value", "");
        EditText agent = Ui.edit(host, "Browser User-Agent", "");
        Ui.add(form, Ui.sub(host, "Use the Cookie header and User-Agent from the same signed-in browser session."));
        Ui.add(form, cookie); Ui.add(form, agent);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle("V1 manual sign-in").setView(form)
                .setPositiveButton("Connect", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                host.session.save(cookie.getText().toString(), agent.getText().toString());
                host.work(() -> host.api.get("api/profile", true), profile -> {
                    dialog.dismiss(); host.refreshProfileNavigation(); showProfile();
                }, e -> { host.session.clearNative(); host.error(e); });
            } catch (Exception e) { host.error(e); }
        }));
        dialog.show();
    }

    private void showAccounts() {
        Dialog dialog = fullDialog();
        LinearLayout body = Ui.vertical(host); Ui.pad(body, 16);
        body.addView(backIcon(() -> dialog.dismiss()),
                new LinearLayout.LayoutParams(Ui.dp(host, 44), Ui.dp(host, 44)));
        Ui.add(body, Ui.text(host, "Switch account", 23, true));
        Ui.add(body, Ui.button(host, "Add another account", v -> {
            dialog.dismiss(); host.webSignIn(true);
        }));
        LinearLayout list = Ui.vertical(host);
        Ui.add(body, list);
        Ui.add(body, Ui.button(host, "Refresh", v -> loadAccounts(list, dialog)));
        dialog.setContentView(Ui.scroll(host, body)); showFull(dialog);
        loadAccounts(list, dialog);
    }

    private void loadAccounts(LinearLayout list, Dialog dialog) {
        list.removeAllViews(); Ui.add(list, Ui.sub(host, "Loading accounts…"));
        host.work(() -> host.api.get("api/accounts", true), response -> {
            list.removeAllViews();
            JSONArray accounts = Json.array(response, "accounts");
            if (accounts.length() == 0) Ui.add(list, Ui.sub(host, "No additional accounts."));
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject account = Json.at(accounts, i);
                String token = Json.str(account, "sessionToken");
                Button row = Ui.button(host, Json.first(account, "displayName", "handle") +
                        "  @" + Json.str(account, "handle") +
                        (Json.bool(account, "active") ? "  ✓" : ""), v -> {
                    if (Json.bool(account, "active")) return;
                    String previousCookie = host.session.cookie();
                    String previousAgent = host.session.userAgent();
                    host.work(() -> {
                        JSONObject result = host.api.send("POST", "api/auth/multi-session/set-active",
                                Json.make("sessionToken", token));
                        if (result.optJSONObject("session") == null) throw new IllegalStateException("Account could not be activated.");
                        String all = android.webkit.CookieManager.getInstance().getCookie("https://smolish.com/");
                        if (all == null) throw new IllegalStateException("Browser cookies are unavailable.");
                        String suffix = "_multi-" + token.toLowerCase();
                        String promote = null;
                        for (String piece : all.split(";")) {
                            String item = piece.trim(); int equal = item.indexOf('=');
                            if (equal < 1) continue;
                            String name = item.substring(0, equal);
                            if (name.toLowerCase().endsWith(suffix)) {
                                promote = name.substring(0, name.length() - suffix.length()) + item.substring(equal);
                                break;
                            }
                        }
                        if (promote == null) throw new IllegalStateException("Account browser cookie was not found. Sign in again.");
                        android.webkit.CookieManager.getInstance().setCookie("https://smolish.com/", promote + "; Path=/; Secure");
                        android.webkit.CookieManager.getInstance().flush();
                        host.session.save(android.webkit.CookieManager.getInstance().getCookie("https://smolish.com/"), previousAgent);
                        JSONObject profile = host.api.get("api/profile", true);
                        String expected = Json.str(account, "handle");
                        if (!expected.isEmpty() && !expected.equalsIgnoreCase(Json.str(profile, "handle"))) {
                            host.session.save(previousCookie, previousAgent);
                            throw new IllegalStateException("Smolish did not activate the selected account.");
                        }
                        return profile;
                    }, profile -> { dialog.dismiss(); host.refreshProfileNavigation(); showProfile(); }, host::error);
                });
                row.setEnabled(!Json.bool(account, "active"));
                Ui.add(list, row);
            }
        }, host::error);
    }

    void showNotifications() {
        LinearLayout root = Ui.vertical(host); Ui.pad(root, 12);
        if (!host.session.signedIn()) {
            Ui.add(root, Ui.emptyState(host, "♧", "Stay in the loop",
                    "Sign in to see new activity from creators and conversations."));
            Ui.add(root, Ui.primary(host, "Go to Profile", v -> host.navigate("Profile")));
            host.setScreen(root); return;
        }
        NotificationState state = new NotificationState();
        LinearLayout actions = Ui.horizontal(host);
        actions.addView(Ui.button(host, "Refresh", v -> showNotifications()), new LinearLayout.LayoutParams(0, -2, 1));
        Button markRead = Ui.button(host, "Mark all read", v -> host.work(
                () -> host.api.send("POST", "api/notifications/read", new JSONObject()),
                response -> {
                    state.markedRead = true;
                    for (View row : state.unreadRows) row.setBackground(Ui.round(host, Ui.CARD, 14));
                    for (TextView heading : state.unreadHeadings)
                        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
                    for (View dot : state.unreadDots) dot.setVisibility(View.GONE);
                    host.clearLocalNotificationReads();
                    host.setUnreadCount(0);
                    v.setEnabled(false);
                }));
        actions.addView(markRead, new LinearLayout.LayoutParams(0, -2, 1));
        Ui.add(root, actions);
        LinearLayout list = Ui.vertical(host);
        ScrollView scroll = Ui.scroll(host, list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        host.setScreen(root);
        Ui.autoLoad(scroll, list, () -> loadNotifications(state, list, scroll));
        loadNotifications(state, list, scroll);
    }

    private static final class NotificationState {
        int page;
        boolean loading;
        boolean done;
        boolean markedRead;
        final HashSet<String> ids = new HashSet<>();
        final ArrayList<View> unreadRows = new ArrayList<>();
        final ArrayList<TextView> unreadHeadings = new ArrayList<>();
        final ArrayList<View> unreadDots = new ArrayList<>();
    }

    private void loadNotifications(NotificationState state, LinearLayout list, ScrollView scroll) {
        if (state.loading || state.done || !scroll.isAttachedToWindow()) return;
        state.loading = true;
        int page = state.page + 1;
        host.work(() -> host.api.get("api/notifications", true, "page", String.valueOf(page)), response -> {
            if (!scroll.isAttachedToWindow()) return;
            state.loading = false;
            state.page = page;
            if (page == 1) list.removeAllViews();
            JSONArray items = Json.array(response, "items", "notifications");
            if (items.length() == 0 && page == 1) Ui.add(list, Ui.sub(host, "No notifications yet."));
            int added = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject note = Json.at(items, i);
                if (!state.ids.add(Json.str(note, "id"))) continue;
                added++;
                String title = Json.first(note, "title", "actorName", "type");
                if (title.isEmpty()) title = "New activity";
                String body = Json.first(note, "body", "message");
                String notificationId = Json.str(note, "id");
                boolean unread = !state.markedRead && note.isNull("readAt") &&
                        !host.isNotificationReadLocally(notificationId);
                LinearLayout row = Ui.horizontal(host);
                row.setGravity(Gravity.TOP);
                row.setBackground(Ui.round(host, unread ? 0xFF192833 : Ui.CARD, 14));
                row.setPadding(Ui.dp(host, 12), Ui.dp(host, 12), Ui.dp(host, 12), Ui.dp(host, 12));
                String avatarUrl = Json.str(note, "actorAvatar");
                View avatar;
                if (avatarUrl.isEmpty()) {
                    IconView bell = new IconView(host, "bell");
                    bell.setInk(Ui.BLUE);
                    bell.setBackground(Ui.round(host, 0xFF203746, 21));
                    avatar = bell;
                } else avatar = Images.loadCircle(host, avatarUrl, 42);
                row.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(host, 42), Ui.dp(host, 42)));
                LinearLayout detail = Ui.vertical(host);
                detail.setBackgroundColor(Color.TRANSPARENT);
                TextView heading = Ui.text(host, title, 15, unread);
                if (unread) { state.unreadRows.add(row); state.unreadHeadings.add(heading); }
                Ui.add(detail, heading);
                if (!body.isEmpty()) Ui.add(detail, Ui.text(host, body, 14, false));
                String actor = Json.str(note, "actorHandle");
                if (!actor.isEmpty()) Ui.add(detail, Ui.sub(host, "From @" + actor));
                String type = Json.str(note, "type").replace('_', ' ');
                String time = relativeTime(Json.str(note, "createdAt"));
                String meta = (type.isEmpty() ? "Activity" : type) + (time.isEmpty() ? "" : "  ·  " + time);
                Ui.add(detail, Ui.sub(host, meta));
                String videoId = Json.str(note, "videoId");
                if (!videoId.isEmpty()) {
                    TextView link = Ui.sub(host, "View video  ↗");
                    link.setTextColor(Ui.BLUE);
                    Ui.add(detail, link);
                }
                LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1);
                detailParams.leftMargin = Ui.dp(host, 12);
                row.addView(detail, detailParams);
                View[] unreadDot = {null};
                if (unread) {
                    View dot = new View(host);
                    dot.setBackground(Ui.round(host, Ui.BLUE, 5));
                    row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(host, 8), Ui.dp(host, 8)));
                    state.unreadDots.add(dot);
                    unreadDot[0] = dot;
                }
                final String notificationTitle = title;
                final String notificationBody = body;
                row.setOnClickListener(v -> {
                    if (unread) {
                        host.markNotificationReadLocally(notificationId);
                        row.setBackground(Ui.round(host, Ui.CARD, 14));
                        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
                        if (unreadDot[0] != null) unreadDot[0].setVisibility(View.GONE);
                    }
                    if (!videoId.isEmpty()) host.openWeb("https://smolish.com/v/" + videoId);
                    else new AlertDialog.Builder(host).setTitle(notificationTitle)
                            .setMessage(notificationBody.isEmpty() ? meta : notificationBody + "\n\n" + meta)
                            .setPositiveButton("OK", null).show();
                });
                Ui.add(list, row);
            }
            int pages = Json.integer(response, "pages");
            state.done = pages > 0 ? page >= pages : items.length() == 0 || added == 0;
            if (!state.done) scroll.post(() -> { if (Ui.nearEnd(scroll)) loadNotifications(state, list, scroll); });
        }, e -> { state.loading = false; host.error(e); });
    }

    private String relativeTime(String value) {
        if (value.isEmpty()) return "";
        try {
            long minutes = Math.max(0, Duration.between(Instant.parse(value), Instant.now()).toMinutes());
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + "m ago";
            if (minutes < 1440) return minutes / 60 + "h ago";
            if (minutes < 10080) return minutes / 1440 + "d ago";
            return value.substring(0, Math.min(10, value.length()));
        } catch (Exception ignored) { return value.substring(0, Math.min(10, value.length())); }
    }

    private static final class CommentState {
        final ArrayList<JSONObject> pinned = new ArrayList<>();
        final ArrayList<JSONObject> items = new ArrayList<>();
        final HashMap<String, JSONArray> replies = new HashMap<>();
        final HashSet<String> expanded = new HashSet<>();
        final HashSet<String> cursors = new HashSet<>();
        String cursor;
        JSONObject replyTarget;
        boolean enabled = true;
        boolean loading;
        ScrollView scroll;
    }

    void showComments(String videoId) {
        BottomSheetDialog dialog = new BottomSheetDialog(host);
        CommentState state = new CommentState();
        LinearLayout root = Ui.vertical(host);
        root.setBackground(Ui.round(host, Ui.CARD, 22));
        root.setPadding(Ui.dp(host, 12), Ui.dp(host, 8), Ui.dp(host, 12), Ui.dp(host, 12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(Ui.dp(host, 12), Ui.dp(host, 8),
                    Ui.dp(host, 12), Math.max(Ui.dp(host, 12), insets.getSystemWindowInsetBottom()));
            return insets;
        });
        View drag = new View(host);
        drag.setBackground(Ui.round(host, 0xFF69696D, 3));
        LinearLayout dragArea = Ui.vertical(host);
        dragArea.setBackgroundColor(Color.TRANSPARENT);
        dragArea.setGravity(Gravity.CENTER);
        dragArea.addView(drag, new LinearLayout.LayoutParams(Ui.dp(host, 38), Ui.dp(host, 4)));
        root.addView(dragArea, new LinearLayout.LayoutParams(-1, Ui.dp(host, 24)));
        dragArea.setOnClickListener(v -> dialog.getBehavior().setState(
                dialog.getBehavior().getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_HALF_EXPANDED : BottomSheetBehavior.STATE_EXPANDED));
        LinearLayout header = Ui.horizontal(host);
        IconView back = new IconView(host, "back");
        back.setContentDescription("Close comments");
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(host, 40), Ui.dp(host, 40)));
        header.addView(Ui.text(host, "Comments", 19, true), new LinearLayout.LayoutParams(0, -2, 1));
        Ui.add(root, header);
        LinearLayout list = Ui.vertical(host);
        list.setBackgroundColor(Color.TRANSPARENT);
        state.scroll = Ui.scroll(host, list);
        root.addView(state.scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.autoLoad(state.scroll, list, () -> loadComments(videoId, state, list, dialog, false));
        LinearLayout controls = Ui.vertical(host);
        controls.setBackgroundColor(Ui.CARD);
        controls.setPadding(0, Ui.dp(host, 6), 0, Ui.dp(host, 12));
        LinearLayout suggestions = Ui.vertical(host);
        suggestions.setBackgroundColor(Color.TRANSPARENT);
        TextView replyStatus = Ui.sub(host, "");
        replyStatus.setId(0x534d02);
        replyStatus.setVisibility(View.GONE);
        replyStatus.setOnClickListener(v -> {
            state.replyTarget = null;
            replyStatus.setVisibility(View.GONE);
        });
        Ui.add(controls, replyStatus);
        Ui.add(controls, suggestions);
        EditText draft = Ui.edit(host, "Add a comment", "");
        draft.setId(0x534d01);
        draft.setOnFocusChangeListener((view, focused) -> {
            if (focused) dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        LinearLayout composer = Ui.horizontal(host);
        composer.addView(draft, new LinearLayout.LayoutParams(0, Ui.dp(host, 46), 1));
        Button post = Ui.primary(host, "↑", v -> {
            if (!host.requireAuth()) return;
            String body = draft.getText().toString().trim();
            if (body.isEmpty()) return;
            String parent = state.replyTarget == null ? null :
                    Json.first(state.replyTarget, "parentId", "id");
            v.setEnabled(false);
            host.work(() -> host.api.send("POST", "api/comments", Json.make(
                    "videoId", videoId, "body", body, "gifSlug", JSONObject.NULL,
                    "parentId", parent == null ? JSONObject.NULL : parent)), response -> {
                v.setEnabled(true);
                draft.setText(""); state.replyTarget = null; suggestions.removeAllViews();
                replyStatus.setVisibility(View.GONE);
                loadComments(videoId, state, list, dialog, true);
            }, e -> { v.setEnabled(true); host.error(e); });
        });
        post.setId(0x534d03);
        post.setTextSize(22);
        post.setContentDescription("Post comment");
        post.setBackground(Ui.round(host, Ui.BLUE, 23));
        LinearLayout.LayoutParams postParams = new LinearLayout.LayoutParams(Ui.dp(host, 46), Ui.dp(host, 46));
        postParams.leftMargin = Ui.dp(host, 8);
        composer.addView(post, postParams);
        Ui.add(controls, composer);
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        draft.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                int space = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n'));
                String token = text.substring(space + 1);
                if (!token.matches("@[A-Za-z0-9_]*")) { suggestions.removeAllViews(); return; }
                String query = token.substring(1);
                host.work(() -> host.api.get("api/mentions", false, "q", query), response -> {
                    if (!draft.getText().toString().endsWith(token)) return;
                    suggestions.removeAllViews();
                    JSONArray people = Json.array(response, "items");
                    for (int i = 0; i < Math.min(people.length(), 5); i++) {
                        String handle = Json.str(Json.at(people, i), "handle");
                        Ui.add(suggestions, Ui.button(host, "@" + handle, v -> {
                            String now = draft.getText().toString();
                            int at = now.lastIndexOf('@');
                            if (at >= 0) draft.setText(now.substring(0, at) + "@" + handle + " ");
                            draft.setSelection(draft.length());
                            suggestions.removeAllViews();
                        }));
                    }
                }, e -> suggestions.removeAllViews());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setNavigationBarColor(Ui.CARD);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.requestLayout();
        }
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setFitToContents(false);
        behavior.setHalfExpandedRatio(0.55f);
        behavior.setPeekHeight(Math.round(host.getResources().getDisplayMetrics().heightPixels * 0.55f));
        if (sheet != null) {
            behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override public void onStateChanged(View bottomSheet, int newState) {
                    syncCommentsControls(bottomSheet, controls, list);
                }
                @Override public void onSlide(View bottomSheet, float slideOffset) {
                    syncCommentsControls(bottomSheet, controls, list);
                }
            });
            sheet.addOnLayoutChangeListener((view, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) -> syncCommentsControls(view, controls, list));
        }
        behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        if (sheet != null) {
            sheet.post(() -> syncCommentsControls(sheet, controls, list));
            controls.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) ->
                    syncCommentsControls(sheet, controls, list));
        }
        loadComments(videoId, state, list, dialog, true);
    }

    private void syncCommentsControls(View sheet, View controls, LinearLayout list) {
        View parent = (View) sheet.getParent();
        if (parent == null || parent.getHeight() == 0) return;
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        int visible = behavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED
                ? Math.round(parent.getHeight() * behavior.getHalfExpandedRatio())
                : parent.getHeight() - sheet.getTop();
        visible = Math.max(Ui.dp(host, 220), visible);
        int hidden = Math.max(0, sheet.getHeight() - visible);
        controls.setTranslationY(-hidden);
        int bottomSpace = hidden + controls.getHeight();
        if (list.getPaddingBottom() != bottomSpace)
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottomSpace);
    }

    private void loadComments(String videoId, CommentState state, LinearLayout list, Dialog dialog, boolean reset) {
        if (state.loading) return;
        if (reset) { state.cursor = null; state.cursors.clear(); }
        String next = reset ? null : state.cursor;
        if (!reset && (next == null || next.isEmpty() || !state.cursors.add(next))) return;
        state.loading = true;
        host.work(() -> host.api.get("api/comments", false, "videoId", videoId, "cursor", next), response -> {
            if (!dialog.isShowing()) return;
            state.loading = false;
            if (reset) { state.items.clear(); state.pinned.clear(); state.replies.clear(); state.expanded.clear(); }
            JSONArray pinned = Json.array(response, "pinned");
            JSONArray items = Json.array(response, "items");
            if (reset) for (int i = 0; i < pinned.length(); i++) state.pinned.add(Json.at(pinned, i));
            HashSet<String> seen = new HashSet<>();
            for (JSONObject item : state.items) seen.add(Json.str(item, "id"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = Json.at(items, i);
                if (seen.add(Json.str(item, "id"))) state.items.add(item);
            }
            state.cursor = Json.str(response, "nextCursor");
            if (state.cursors.contains(state.cursor)) state.cursor = "";
            state.enabled = response.optBoolean("commentsEnabled", true);
            Button post = (Button) dialog.findViewById(0x534d03);
            if (post != null) post.setEnabled(state.enabled);
            renderComments(videoId, state, list, dialog);
            if (!state.cursor.isEmpty() && state.scroll != null)
                state.scroll.post(() -> { if (Ui.nearEnd(state.scroll)) loadComments(videoId, state, list, dialog, false); });
        }, e -> { state.loading = false; if (next != null) state.cursors.remove(next); host.error(e); });
    }

    private void renderComments(String videoId, CommentState state, LinearLayout list, Dialog dialog) {
        list.removeAllViews();
        if (state.pinned.isEmpty() && state.items.isEmpty()) Ui.add(list, Ui.sub(host, "No comments yet."));
        if (!state.enabled) Ui.add(list, Ui.sub(host, "Comments are disabled for this video."));
        if (!state.pinned.isEmpty()) Ui.add(list, Ui.text(host, "Pinned", 16, true));
        for (JSONObject item : state.pinned) commentRow(videoId, item, state, list, dialog, true);
        if (!state.items.isEmpty()) Ui.add(list, Ui.text(host, "All comments", 16, true));
        for (JSONObject item : state.items) commentRow(videoId, item, state, list, dialog, false);
    }

    private void commentRow(String videoId, JSONObject comment, CommentState state,
                            LinearLayout list, Dialog dialog, boolean pinned) {
        String id = Json.str(comment, "id");
        LinearLayout card = Ui.vertical(host);
        GradientDrawable cardBackground = Ui.round(host, 0xFF26262B, 14);
        cardBackground.setStroke(Ui.dp(host, 1), 0xFF38383E);
        card.setBackground(cardBackground);
        card.setPadding(Ui.dp(host, 11), Ui.dp(host, 10), Ui.dp(host, 11), Ui.dp(host, 8));
        LinearLayout identity = Ui.horizontal(host);
        String avatarUrl = Json.str(comment, "authorAvatar");
        ImageView avatar = Images.loadCircle(host, avatarUrl, 34);
        identity.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(host, 34), Ui.dp(host, 34)));
        LinearLayout byline = Ui.vertical(host);
        byline.setBackgroundColor(Color.TRANSPARENT);
        TextView name = Ui.text(host, Json.first(comment, "authorName", "authorHandle"), 13, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        byline.addView(name);
        TextView handle = Ui.sub(host, "@" + Json.str(comment, "authorHandle"));
        handle.setTextSize(11);
        handle.setSingleLine(true);
        handle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        byline.addView(handle);
        LinearLayout.LayoutParams bylineParams = new LinearLayout.LayoutParams(0, -2, 1);
        bylineParams.leftMargin = Ui.dp(host, 9);
        identity.addView(byline, bylineParams);
        String age = relativeTime(Json.str(comment, "createdAt"));
        TextView meta = Ui.sub(host, pinned ? "Pinned" : age);
        meta.setTextSize(11);
        if (pinned) meta.setTextColor(Ui.BLUE);
        identity.addView(meta);
        card.addView(identity);
        String body = Json.str(comment, "body").trim();
        if (body.isEmpty()) body = Json.str(comment, "gifSlug").isEmpty() ? "" : "GIF";
        if (!body.isEmpty()) {
            TextView content = Ui.text(host, body, 14, false);
            content.setLineSpacing(Ui.dp(host, 2), 1f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = Ui.dp(host, 7);
            card.addView(content, bodyParams);
        }
        LinearLayout actions = Ui.horizontal(host);
        boolean[] liked = {"like".equals(Json.str(comment, "viewerReaction"))};
        TextView react = Ui.sub(host, (liked[0] ? "♥ " : "♡ ") + Json.integer(comment, "likesCount"));
        react.setTextSize(12);
        react.setTextColor(liked[0] ? Ui.BLUE : Ui.SUB);
        react.setPadding(0, Ui.dp(host, 8), Ui.dp(host, 20), Ui.dp(host, 4));
        react.setOnClickListener(v -> {
            if (!host.requireAuth()) return;
            boolean next = !liked[0];
            host.work(() -> host.api.send("POST", "api/comments/reactions",
                    Json.make("commentId", id, "reaction", next ? "like" : JSONObject.NULL)), r -> {
                liked[0] = next;
                react.setText((next ? "♥ " : "♡ ") +
                        (Json.integer(comment, "likesCount") + (next ? 1 : 0)));
                react.setTextColor(next ? Ui.BLUE : Ui.SUB);
            });
        });
        actions.addView(react);
        TextView reply = Ui.sub(host, "Reply");
        reply.setTextSize(12);
        reply.setPadding(0, Ui.dp(host, 8), Ui.dp(host, 20), Ui.dp(host, 4));
        reply.setOnClickListener(v -> {
            state.replyTarget = comment;
            EditText input = (EditText) dialog.findViewById(0x534d01);
            if (input != null) input.setText("@" + Json.str(comment, "authorHandle") + " ");
            TextView status = (TextView) dialog.findViewById(0x534d02);
            if (status != null) {
                status.setText("Replying to @" + Json.str(comment, "authorHandle") + "   ·   Tap to cancel");
                status.setVisibility(View.VISIBLE);
            }
        });
        actions.addView(reply);
        if (Json.integer(comment, "repliesCount") > 0) {
            TextView repliesAction = Ui.sub(host, state.expanded.contains(id) ? "Hide replies" :
                    Json.integer(comment, "repliesCount") + " replies");
            repliesAction.setTextSize(12);
            repliesAction.setTextColor(Ui.BLUE);
            repliesAction.setPadding(0, Ui.dp(host, 8), 0, Ui.dp(host, 4));
            repliesAction.setOnClickListener(v -> {
                if (state.expanded.remove(id)) { renderComments(videoId, state, list, dialog); return; }
                state.expanded.add(id);
                if (state.replies.containsKey(id)) { renderComments(videoId, state, list, dialog); return; }
                host.work(() -> host.api.get("api/comments", false, "videoId", videoId, "parentId", id),
                        response -> { state.replies.put(id, Json.array(response, "items")); renderComments(videoId, state, list, dialog); });
            });
            actions.addView(repliesAction);
        }
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = Ui.dp(host, 5);
        card.addView(actions, actionParams);
        Ui.add(list, card);
        if (state.expanded.contains(id)) {
            JSONArray replies = state.replies.get(id);
            if (replies != null) for (int i = 0; i < replies.length(); i++) {
                JSONObject child = Json.at(replies, i);
                TextView text = Ui.text(host, "  ↳ @" + Json.str(child, "authorHandle") +
                        ": " + Json.str(child, "body"), 13, false);
                text.setBackground(Ui.round(host, 0xFF25252A, 10)); Ui.pad(text, 10); Ui.add(list, text);
            }
        }
    }

    private Dialog fullDialog() { return new Dialog(host, android.R.style.Theme_Material_NoActionBar_Fullscreen); }
    private IconView backIcon(Runnable action) {
        IconView back = new IconView(host, "back");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> action.run());
        return back;
    }
    private void showFull(Dialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setStatusBarColor(Ui.BG);
            dialog.getWindow().setNavigationBarColor(Ui.BG);
            View content = dialog.findViewById(android.R.id.content);
            if (content != null) content.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            });
        }
    }
}
