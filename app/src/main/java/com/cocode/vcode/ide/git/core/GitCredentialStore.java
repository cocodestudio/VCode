package com.cocode.vcode.ide.git.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages encrypted Git credentials using Android KeyStore and AES-GCM encryption.
 * Also stores local author names and emails for unauthenticated commits.
 */
public class GitCredentialStore {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEYSTORE_ALIAS = "VCodeGitKey";
    private static final String PREFS_NAME = "vcode_git_credentials";
    private static final String KEY_ENC_TOKEN = "vcode_enc_token";
    private static final String KEY_USERNAME = "vcode_git_username";

    private static final String KEY_LOCAL_NAME = "vcode_local_author_name";
    private static final String KEY_LOCAL_EMAIL = "vcode_local_author_email";

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final String SEPARATOR = "::";

    // Key Management

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
        ks.load(null);
        if (!ks.containsAlias(KEYSTORE_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            kg.init(new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) ks.getEntry(KEYSTORE_ALIAS, null);
        if (entry == null) throw new Exception("Keystore entry missing");
        return entry.getSecretKey();
    }

    // Public API

    /**
     * Encrypts and saves the given personal access token to SharedPreferences.
     */
    public void saveToken(Context ctx, String token) throws Exception {
        if (token == null || token.isEmpty()) throw new Exception("Token is empty");
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

        String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
        String encB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        String combined = ivB64 + SEPARATOR + encB64;

        getPrefs(ctx).edit().putString(KEY_ENC_TOKEN, combined).apply();
    }

    /**
     * Decrypts and returns the stored personal access token, or null if none exists.
     */
    public String getToken(Context ctx) throws Exception {
        String combined = getPrefs(ctx).getString(KEY_ENC_TOKEN, null);
        if (combined == null || !combined.contains(SEPARATOR)) return null;
        String[] parts = combined.split(SEPARATOR, 2);

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public void saveUsername(Context ctx, String username) {
        if (username == null) return;
        getPrefs(ctx).edit().putString(KEY_USERNAME, username).apply();
    }

    public String getUsername(Context ctx) {
        return getPrefs(ctx).getString(KEY_USERNAME, null);
    }

    // Local Author Info

    public void saveLocalAuthor(Context ctx, String name, String email) {
        if (name == null || email == null) return;
        getPrefs(ctx).edit()
                .putString(KEY_LOCAL_NAME, name.trim())
                .putString(KEY_LOCAL_EMAIL, email.trim())
                .apply();
    }

    public String getLocalAuthorName(Context ctx) {
        return getPrefs(ctx).getString(KEY_LOCAL_NAME, "");
    }

    public String getLocalAuthorEmail(Context ctx) {
        return getPrefs(ctx).getString(KEY_LOCAL_EMAIL, "");
    }

    public boolean hasLocalAuthor(Context ctx) {
        return !getLocalAuthorName(ctx).isEmpty() && !getLocalAuthorEmail(ctx).isEmpty();
    }

    /**
     * Clears stored GitHub credentials. Local author info is preserved.
     */
    public void clearCredentials(Context ctx) {
        getPrefs(ctx).edit()
                .remove(KEY_ENC_TOKEN)
                .remove(KEY_USERNAME)
                .apply();
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
            ks.load(null);
            if (ks.containsAlias(KEYSTORE_ALIAS)) ks.deleteEntry(KEYSTORE_ALIAS);
        } catch (Exception e) {
            // Key deletion failure is non-fatal
        }
    }

    public boolean hasCredentials(Context ctx) {
        return getPrefs(ctx).contains(KEY_ENC_TOKEN);
    }

    private SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}