package io.github.rickybtang.leafboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SettingsStore {
    private static final String PREFS = "leafboard.settings";
    private static final String KEY_ALIAS = "leafboard.webdav.password";
    private static final String DEFAULT_URL = "https://dav.jianguoyun.com/dav/leafboard";
    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String webDavUrl() {
        String value = preferences.getString("webdav.url", DEFAULT_URL);
        if (value == null || value.trim().isEmpty()) return DEFAULT_URL;
        return trimSlash(value.trim());
    }

    public String username() {
        return preferences.getString("webdav.username", "");
    }

    public String password() {
        String encrypted = preferences.getString("webdav.password", "");
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(encrypted, Base64.NO_WRAP);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] payload = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public void saveConnection(String url, String username, String password) throws Exception {
        SharedPreferences.Editor editor = preferences.edit()
                .putString("webdav.url", trimSlash(url.trim()))
                .putString("webdav.username", username.trim());
        if (password != null && !password.isEmpty()) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[cipher.getIV().length + encrypted.length];
            System.arraycopy(cipher.getIV(), 0, combined, 0, cipher.getIV().length);
            System.arraycopy(encrypted, 0, combined, cipher.getIV().length, encrypted.length);
            editor.putString("webdav.password", Base64.encodeToString(combined, Base64.NO_WRAP));
        }
        editor.apply();
    }

    public List<String> producerIds() {
        String raw = preferences.getString("producer.ids", "");
        List<String> result = new ArrayList<>();
        if (raw != null) {
            for (String value : raw.split(",")) {
                String id = value.trim();
                if (!id.isEmpty() && !result.contains(id)) result.add(id);
            }
        }
        return result;
    }

    public void setProducerIds(String ids) {
        preferences.edit().putString("producer.ids", ids).apply();
    }

    public int intervalMinutes() {
        return preferences.getInt("sync.interval", 15);
    }

    public void setIntervalMinutes(int value) {
        preferences.edit().putInt("sync.interval", value).apply();
    }

    public boolean quietEnabled() {
        return preferences.getBoolean("quiet.enabled", false);
    }

    public String quietStart() {
        return preferences.getString("quiet.start", "23:00");
    }

    public String quietEnd() {
        return preferences.getString("quiet.end", "07:00");
    }

    public void setQuietHours(boolean enabled, String start, String end) {
        preferences.edit()
                .putBoolean("quiet.enabled", enabled)
                .putString("quiet.start", start)
                .putString("quiet.end", end)
                .apply();
    }

    public boolean isQuietNow() {
        if (!quietEnabled()) return false;
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(quietStart());
            LocalTime end = LocalTime.parse(quietEnd());
            if (start.equals(end)) return true;
            if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
            return !now.isBefore(start) || now.isBefore(end);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
