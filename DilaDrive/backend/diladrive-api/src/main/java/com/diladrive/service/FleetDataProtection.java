package com.diladrive.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class FleetDataProtection {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String encodedMasterKey;

    public FleetDataProtection(
            @Value("${app.driver-data.master-key:}") String encodedMasterKey) {
        this.encodedMasterKey = encodedMasterKey;
    }

    public boolean isConfigured() {
        return encodedMasterKey != null && !encodedMasterKey.isBlank();
    }

    public String encryptIc(String normalizedIc) {
        return "v1:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(encrypt(normalizedIc.getBytes(StandardCharsets.UTF_8), "ic-encryption"));
    }

    public String decryptIc(String encryptedIc) {
        if (encryptedIc == null || !encryptedIc.startsWith("v1:")) {
            throw new IllegalStateException("The stored driver identity value has an unsupported format.");
        }
        byte[] clear = decrypt(Base64.getUrlDecoder().decode(encryptedIc.substring(3)), "ic-encryption");
        return new String(clear, StandardCharsets.UTF_8);
    }

    public String icLookup(String normalizedIc) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(deriveKey("ic-lookup"), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedIc.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Driver identity lookup protection is unavailable.", exception);
        }
    }

    public byte[] encryptDocument(byte[] clear) {
        return encrypt(clear, "document-encryption");
    }

    public byte[] decryptDocument(byte[] encrypted) {
        return decrypt(encrypted, "document-encryption");
    }

    private byte[] encrypt(byte[] clear, String purpose) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(deriveKey(purpose), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(clear);
            byte[] envelope = new byte[1 + nonce.length + ciphertext.length];
            envelope[0] = 1;
            System.arraycopy(nonce, 0, envelope, 1, nonce.length);
            System.arraycopy(ciphertext, 0, envelope, 1 + nonce.length, ciphertext.length);
            return envelope;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive fleet data could not be encrypted.", exception);
        }
    }

    private byte[] decrypt(byte[] envelope, String purpose) {
        if (envelope == null || envelope.length < 1 + NONCE_BYTES + 16 || envelope[0] != 1) {
            throw new IllegalStateException("The stored fleet document has an unsupported format.");
        }
        byte[] nonce = Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, 1 + NONCE_BYTES, envelope.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(deriveKey(purpose), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive fleet data could not be decrypted.", exception);
        }
    }

    private byte[] deriveKey(String purpose) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Set DRIVER_DATA_MASTER_KEY to enable encrypted fleet identity documents.");
        }
        byte[] master;
        try {
            master = Base64.getDecoder().decode(encodedMasterKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DRIVER_DATA_MASTER_KEY must be Base64 encoded.", exception);
        }
        if (master.length != 32) {
            throw new IllegalStateException("DRIVER_DATA_MASTER_KEY must decode to exactly 32 bytes.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(("DilaDrive:" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive fleet data protection is unavailable.", exception);
        } finally {
            Arrays.fill(master, (byte) 0);
        }
    }
}
