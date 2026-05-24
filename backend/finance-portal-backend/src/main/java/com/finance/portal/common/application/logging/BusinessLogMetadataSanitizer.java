package com.finance.portal.common.application.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BusinessLogMetadataSanitizer {

    private static final Set<String> BLOCKED_KEYS = Set.of(
            "password", "currentpassword", "newpassword", "confirmpassword", "usepassword",
            "token", "refreshtoken", "accesstoken", "otp", "verificationcode", "verificationtoken",
            "jwt", "ldapdn", "ldap_entry_dn", "userpassword", "email", "mail", "sessionsecret"
    );

    private BusinessLogMetadataSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String normalizedKey = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (BLOCKED_KEYS.contains(normalizedKey)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String str && looksLikeSensitiveValue(normalizedKey, str)) {
                continue;
            }
            safe.put(entry.getKey(), value);
        }
        return safe.isEmpty() ? null : safe;
    }

    public static String extractEmailDomain(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** E-postayı maskeler: "bagginsbilbo695@gmail.com" → "b***5@gmail.com" (loglarda PII sızmasın). */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return maskedLocal + domain;
    }

    public static String hashEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static boolean looksLikeSensitiveValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.contains("@") && !key.contains("domain") && !key.contains("hash")) {
            return true;
        }
        if (value.length() > 200) {
            return true;
        }
        return false;
    }
}
