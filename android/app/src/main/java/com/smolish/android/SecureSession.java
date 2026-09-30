package com.smolish.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSession {
    private static final String KEY_ALIAS = "smolish_android_session_v1";
    private final SharedPreferences prefs;
    private String cookie = "";
    private String userAgent = "";

    SecureSession(Context context) {
        prefs = context.getSharedPreferences("private_session", Context.MODE_PRIVATE);
        String saved = prefs.getString("session", null);
        if (saved != null) try {
            byte[] bytes = Base64.decode(saved, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(bytes, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] clear = cipher.doFinal(bytes, 12, bytes.length - 12);
            JSONObject json = new JSONObject(new String(clear, StandardCharsets.UTF_8));
            cookie = json.optString("cookie", "");
            userAgent = json.optString("userAgent", "");
        } catch (Exception ignored) { prefs.edit().remove("session").apply(); }
    }

    synchronized boolean signedIn() { return !cookie.isEmpty(); }
    synchronized String cookie() { return cookie; }
    synchronized String userAgent() { return userAgent; }

    synchronized void save(String rawCookie, String agent) throws Exception {
        String normalized = normalize(rawCookie);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Enter a complete Cookie header.");
        JSONObject json = Json.make("cookie", normalized, "userAgent", agent == null ? "" : agent.trim());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        if (!prefs.edit().putString("session", Base64.encodeToString(combined, Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Could not save the session.");
        cookie = normalized;
        userAgent = agent == null ? "" : agent.trim();
    }

    synchronized void clear() {
        clearNative();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
    }

    synchronized void clearNative() {
        cookie = "";
        userAgent = "";
        prefs.edit().remove("session").apply();
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String value = input.trim().replace("\n", "").replace("\r", "");
        if (value.toLowerCase().startsWith("cookie:")) value = value.substring(7).trim();
        StringBuilder out = new StringBuilder();
        for (String part : value.split(";")) {
            String item = part.trim();
            if (!item.contains("=") || item.startsWith("=")) continue;
            if (out.length() > 0) out.append("; ");
            out.append(item);
        }
        return out.toString();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
