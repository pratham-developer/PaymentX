package com.pratham.paymentx.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class HashUtil {

    private static final String ALGORITHM = "HmacSHA256";

    @Value("${hash.key}")
    private String defaultKey;

    public String hash(String plaintext) {
        if (defaultKey == null) {
            throw new IllegalStateException("Default hash key not configured");
        }
        return hash(plaintext, defaultKey);
    }

    public String hash(String plaintext, String specificKey) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty");
        }
        if (specificKey == null || specificKey.isBlank()) {
            throw new IllegalArgumentException("Hashing key cannot be null or empty");
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(specificKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public boolean matches(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
            return false;
        }
        String calculatedHash = hash(plaintext);
        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}