package com.finance.portal.news.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Haber URL'sinden kararlı (deterministik) ve URL-güvenli kısa kimlik üretir.
 * Detay sayfası /api/v1/news/{id} bu kimlikle cache'teki haberi bulur.
 */
public final class NewsIdUtil {

    private NewsIdUtil() {
    }

    public static String idFor(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(url.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }
}
