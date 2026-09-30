package com.smolish.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONObject;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity {
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    SecureSession session;
    Api api;
    private FrameLayout content;
    private LinearLayout navigation;
    private String currentTab = "Feed";
    private VideoView activePlayer;
    private FeedScreen feedScreen;
    private StudioScreen studioScreen;
    private int unread;
    private String profileAvatarUrl = "";
    private final Runnable notificationPoll = new Runnable() {
        @Override public void run() {
            if (session != null && session.signedIn()) {
                work(() -> Json.integer(api.get("api/notifications/unread", true), "unread"), value -> {
                    if (value == 0) clearLocalNotificationReads();
                    setUnreadCount(value - localNotificationReads().size());
                });
            }
            main.postDelayed(this, 60_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        session = new SecureSession(this);
        api = new Api(session);
        LinearLayout root = Ui.vertical(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        View divider = new View(this);
        divider.setBackgroundColor(Ui.BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
        navigation = Ui.horizontal(this);
        navigation.setBackgroundColor(0xFF111111);
        root.addView(navigation, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));
        setContentView(root);
        navigate("Feed");
    }

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(notificationPoll);
        notificationPoll.run();
        refreshProfileNavigation();
    }

    @Override protected void onPause() {
        main.removeCallbacks(notificationPoll);
        if (activePlayer != null) activePlayer.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        io.shutdownNow();
        super.onDestroy();
    }

    void navigate(String tab) {
        if (feedScreen != null) { feedScreen.dispose(); feedScreen = null; }
        releasePlayer();
        currentTab = tab;
        updateNavigation();
        switch (tab) {
            case "Feed": feedScreen = new FeedScreen(this); feedScreen.show(); break;
            case "Search": new SocialScreens(this).showSearch(); break;
            case "Studio": studioScreen = new StudioScreen(this); studioScreen.show(); break;
            case "Alerts": new SocialScreens(this).showNotifications(); break;
            default: new SocialScreens(this).showProfile(); break;
        }
    }

    void setScreen(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }

    void setPlayer(VideoView player) { releasePlayer(); activePlayer = player; }
    void releasePlayer() { if (activePlayer != null) { activePlayer.stopPlayback(); activePlayer = null; } }
    void releasePlayer(VideoView player) { if (activePlayer == player) releasePlayer(); }

    private void updateNavigation() {
        if (navigation == null) return;
        navigation.removeAllViews();
        String[] tabs = {"Feed", "Search", "Studio", "Alerts", "Profile"};
        String[] icons = {"home", "search", "studio", "bell", "person"};
        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];
            FrameLayout item = new FrameLayout(this);
            item.setBackgroundColor(0xFF111111);
            item.setContentDescription(tab.equals("Alerts") && unread > 0 ? "Alerts, " + unread + " unread" : tab);
            if (tab.equals("Profile") && !profileAvatarUrl.isEmpty()) {
                ImageView avatar = Images.loadCircle(this, profileAvatarUrl, 29);
                FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(
                        Ui.dp(this, 29), Ui.dp(this, 29), android.view.Gravity.CENTER);
                item.addView(avatar, avatarParams);
            } else {
                IconView icon = new IconView(this, icons[i]);
                icon.setInk(tab.equals(currentTab) ? Ui.BLUE : Ui.SUB);
                item.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 44),
                        Ui.dp(this, 44), android.view.Gravity.CENTER));
            }
            if (tab.equals(currentTab)) {
                View active = new View(this);
                active.setBackground(Ui.round(this, Ui.BLUE, 2));
                FrameLayout.LayoutParams dot = new FrameLayout.LayoutParams(Ui.dp(this, 14),
                        Ui.dp(this, 2), android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
                dot.bottomMargin = Ui.dp(this, 2);
                item.addView(active, dot);
            }
            if (tab.equals("Alerts") && unread > 0) {
                View badge = new View(this);
                badge.setBackground(Ui.round(this, Ui.BLUE, 5));
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(Ui.dp(this, 8),
                        Ui.dp(this, 8), android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                badgeParams.topMargin = Ui.dp(this, 8);
                badgeParams.leftMargin = Ui.dp(this, 27);
                item.addView(badge, badgeParams);
            }
            navigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
            item.setOnClickListener(v -> navigate(tab));
        }
    }

    void refreshProfileNavigation() {
        if (session == null || !session.signedIn()) {
            profileAvatarUrl = "";
            updateNavigation();
            return;
        }
        work(() -> Json.first(api.get("api/profile", true), "avatar", "avatarUrl"), this::setProfileAvatar, e -> {});
    }

    void setProfileAvatar(String url) {
        profileAvatarUrl = url == null ? "" : url;
        updateNavigation();
    }

    void setUnreadCount(int count) {
        unread = Math.max(0, count);
        updateNavigation();
    }

    private String notificationReadKey() {
        return "seen_" + Integer.toHexString(session.cookie().hashCode());
    }

    private Set<String> localNotificationReads() {
        return new HashSet<>(getSharedPreferences("notification_reads", Context.MODE_PRIVATE)
                .getStringSet(notificationReadKey(), java.util.Collections.emptySet()));
    }

    boolean isNotificationReadLocally(String id) {
        return !id.isEmpty() && localNotificationReads().contains(id);
    }

    void markNotificationReadLocally(String id) {
        if (id.isEmpty()) return;
        Set<String> seen = localNotificationReads();
        if (!seen.add(id)) return;
        getSharedPreferences("notification_reads", Context.MODE_PRIVATE).edit()
                .putStringSet(notificationReadKey(), seen).apply();
        setUnreadCount(unread - 1);
    }

    void clearLocalNotificationReads() {
        getSharedPreferences("notification_reads", Context.MODE_PRIVATE).edit()
                .remove(notificationReadKey()).apply();
    }

    <T> void work(Callable<T> task, Consumer<T> success) { work(task, success, this::error); }
    <T> void work(Callable<T> task, Consumer<T> success, Consumer<Exception> failure) {
        io.execute(() -> {
            try { T result = task.call(); main.post(() -> success.accept(result)); }
            catch (Exception e) { main.post(() -> failure.accept(e)); }
        });
    }

    void error(Exception e) { new AlertDialog.Builder(this).setTitle("Could not complete action")
            .setMessage(e.getMessage() == null ? "Please try again." : e.getMessage()).setPositiveButton("OK", null).show(); }
    void message(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    boolean requireAuth() {
        if (session.signedIn()) return true;
        new AlertDialog.Builder(this).setTitle("Sign in required")
                .setMessage("Connect your Smolish account from Profile to use this feature.")
                .setPositiveButton("Go to Profile", (d, w) -> navigate("Profile"))
                .setNegativeButton("Cancel", null).show();
        return false;
    }
    void webSignIn(boolean addAccount) {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra("addAccount", addAccount);
        startActivityForResult(intent, 100);
    }
    void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 101);
    }
    void share(String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, "Share Smolish link"));
    }
    void openWeb(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
    }
    void showVideo(JSONObject video) {
        FeedScreen screen = currentTab.equals("Feed") && feedScreen != null ? feedScreen : new FeedScreen(this);
        screen.showVideoDialog(video);
    }
    void showCreator(JSONObject creator) { new SocialScreens(this).showCreator(creator); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == 100) {
            session = new SecureSession(this);
            api = new Api(session);
            profileAvatarUrl = "";
            refreshProfileNavigation();
            navigate("Profile");
        }
        if (requestCode == 101 && data != null && data.getData() != null) {
            try { getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) {}
            if (studioScreen == null) { navigate("Studio"); }
            studioScreen.onVideoSelected(data.getData());
        }
    }
}
