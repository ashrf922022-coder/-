package com.awridi.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class EncryptedPrefsHelper {

    private static final String KEY_ALIAS = "AWRIDI_SECURE_KEY";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";

    public static void saveSecureString(Context context, SharedPreferences prefs, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            prefs.edit().remove(key).remove(key + "_enc").apply();
            return;
        }
        try {
            String encrypted = encryptString(value);
            if (encrypted != null) {
                // Save encrypted string and remove any leftover unencrypted plaintext string
                prefs.edit().putString(key + "_enc", encrypted).remove(key).apply();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback only if KeyStore cipher encryption is unavailable
        prefs.edit().putString(key, value).apply();
    }

    public static String getSecureString(SharedPreferences prefs, String key, String defaultValue) {
        String encVal = prefs.getString(key + "_enc", null);
        if (encVal != null) {
            try {
                String decrypted = decryptString(encVal);
                if (decrypted != null && !decrypted.isEmpty()) {
                    return decrypted;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return prefs.getString(key, defaultValue);
    }

    private static synchronized SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE);
            keyGenerator.init(
                    new android.security.keystore.KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setRandomizedEncryptionRequired(true)
                            .build()
            );
            return keyGenerator.generateKey();
        } else {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }
    }

    private static String encryptString(String plainText) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        return Base64.encodeToString(combined, Base64.DEFAULT);
    }

    private static String decryptString(String encryptedBase64) throws Exception {
        byte[] combined = Base64.decode(encryptedBase64, Base64.DEFAULT);
        if (combined.length <= 12) return null;

        byte[] iv = new byte[12];
        byte[] encryptedBytes = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.length);

        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
