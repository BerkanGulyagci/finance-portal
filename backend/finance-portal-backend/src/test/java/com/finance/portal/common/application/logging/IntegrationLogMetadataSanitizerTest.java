package com.finance.portal.common.application.logging;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntegrationLogMetadataSanitizerTest {

    @Test
    void sanitize_returnsNullForNullOrEmpty() {
        assertNull(IntegrationLogMetadataSanitizer.sanitize(null));
        assertNull(IntegrationLogMetadataSanitizer.sanitize(Map.of()));
    }

    @Test
    void sanitize_removesBlockedSensitiveKeys() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", "yahoo");
        metadata.put("symbol", "THYAO");
        metadata.put("apiKey", "secret-key");
        metadata.put("api_key", "secret-key");
        metadata.put("token", "jwt");
        metadata.put("authorization", "Bearer x");
        metadata.put("header", "value");
        metadata.put("fullUrl", "https://example.com?q=secret");
        metadata.put("fullQuery", "apiKey=secret");
        metadata.put("responseBody", "{\"data\":[]}");
        metadata.put("requestBody", "{\"password\":\"x\"}");
        metadata.put("html", "<html></html>");
        metadata.put("htmlContent", "<div></div>");
        metadata.put("news.api.key", "news-key");
        metadata.put("coingecko.api.key", "cg-key");
        metadata.put("evds.api.key", "evds-key");

        Map<String, Object> safe = IntegrationLogMetadataSanitizer.sanitize(metadata);

        assertNotNull(safe);
        assertEquals("yahoo", safe.get("provider"));
        assertEquals("THYAO", safe.get("symbol"));
        assertEquals(2, safe.size());
    }

    @Test
    void sanitize_removesUrlLikeValuesForUrlKeys() {
        Map<String, Object> metadata = Map.of(
                "symbol", "BTC",
                "requestUrl", "https://api.example.com?apiKey=secret"
        );
        Map<String, Object> safe = IntegrationLogMetadataSanitizer.sanitize(metadata);
        assertNotNull(safe);
        assertEquals("BTC", safe.get("symbol"));
        assertFalse(safe.containsKey("requestUrl"));
    }

    @Test
    void sanitize_removesVeryLongStringValues() {
        String longValue = "x".repeat(600);
        Map<String, Object> metadata = Map.of("note", longValue, "symbol", "ETH");
        Map<String, Object> safe = IntegrationLogMetadataSanitizer.sanitize(metadata);
        assertNotNull(safe);
        assertEquals("ETH", safe.get("symbol"));
        assertFalse(safe.containsKey("note"));
    }
}
