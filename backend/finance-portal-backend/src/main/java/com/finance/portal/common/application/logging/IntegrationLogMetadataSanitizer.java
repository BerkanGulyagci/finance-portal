package com.finance.portal.common.application.logging;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class IntegrationLogMetadataSanitizer {

    private static final Set<String> BLOCKED_KEYS = Set.of(
            "apikey", "api_key", "token", "authorization", "header", "headers",
            "fullurl", "fullquery", "responsebody", "requestbody",
            "html", "htmlcontent", "body", "news.api.key", "coingecko.api.key",
            "evds.api.key", "password", "jwt", "accesstoken", "refreshtoken"
    );

    private IntegrationLogMetadataSanitizer() {}

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

    private static boolean looksLikeSensitiveValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("apikey=") || lower.contains("api_key=")) {
            return true;
        }
        if (value.contains("http://") || value.contains("https://")) {
            if (key.contains("url") || key.contains("uri") || key.contains("query")) {
                return true;
            }
        }
        if (value.length() > 500) {
            return true;
        }
        return false;
    }
}
