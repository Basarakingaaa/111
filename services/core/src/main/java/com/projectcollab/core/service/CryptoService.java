package com.projectcollab.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${app.config-encryption-key}") String encodedKey) {
        byte[] raw;
        try { raw = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("APP_CONFIG_ENCRYPTION_KEY must be Base64", e); }
        if (raw.length != 32) throw new IllegalStateException("APP_CONFIG_ENCRYPTION_KEY must decode to exactly 32 bytes");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt protected configuration", e); }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (!ciphertext.startsWith("v1:")) throw new IllegalStateException("Unsupported ciphertext version");
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext.substring(3));
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to decrypt protected configuration", e); }
    }
}

