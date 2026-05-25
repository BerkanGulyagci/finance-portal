package com.finance.portal.news.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.news.application.port.TranslationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ücretsiz Google çeviri uç noktası (gtx; anahtar/konteyner gerektirmez) ile çeviri.
 * Best-effort: hata/timeout/aynı dil → orijinal metni döner. Sonuçlar LRU cache'lenir.
 * NOT: resmi olmayan uçtur (kişisel kullanım); kota/erişim sorununda sessizce orijinale düşer.
 */
@Component
public class GoogleFreeTranslationAdapter implements TranslationPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleFreeTranslationAdapter.class);
    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final int MAX_CHUNK = 1800; // URL uzunluğu güvenli kalsın diye parça boyutu

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = lru(3000);

    public GoogleFreeTranslationAdapter(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(6))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank() || targetLang == null) {
            return text;
        }
        String src = norm(sourceLang);
        String tgt = norm(targetLang);
        if (tgt.isEmpty() || src.equals(tgt)) {
            return text;
        }
        String key = src + ">" + tgt + "|" + text;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            StringBuilder out = new StringBuilder();
            for (String chunk : chunk(text)) {
                out.append(translateChunk(chunk, src, tgt));
            }
            String result = out.toString();
            if (result.isBlank()) {
                return text;
            }
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            log.debug("translate failed ({}>{}): {}", src, tgt, e.getMessage());
            return text; // best-effort
        }
    }

    private String translateChunk(String chunk, String src, String tgt) {
        // URI nesnesi ver (toUriString String'i RestTemplate'in tekrar encode edip %20'leri çift-encode etmesini önler).
        URI uri = UriComponentsBuilder.fromHttpUrl(ENDPOINT)
                .queryParam("client", "gtx")
                .queryParam("sl", src.isEmpty() ? "auto" : src)
                .queryParam("tl", tgt)
                .queryParam("dt", "t")
                .queryParam("q", chunk)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        ResponseEntity<byte[]> resp =
                restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        byte[] body = resp.getBody();
        if (body == null || body.length == 0) {
            return chunk;
        }
        try {
            // Yanıt: [[["çeviri","orijinal",...],["...",...]], ...] — segmentlerin ilk elemanını birleştir.
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode segments = root.get(0);
            if (segments == null || !segments.isArray()) {
                return chunk;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : segments) {
                if (seg != null && seg.isArray() && seg.get(0) != null && !seg.get(0).isNull()) {
                    sb.append(seg.get(0).asText());
                }
            }
            return sb.length() > 0 ? sb.toString() : chunk;
        } catch (Exception e) {
            return chunk;
        }
    }

    /** Uzun metni cümle/boşluk sınırından ~MAX_CHUNK parçalara böler. */
    private static List<String> chunk(String text) {
        if (text.length() <= MAX_CHUNK) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + MAX_CHUNK, text.length());
            if (end < text.length()) {
                int brk = text.lastIndexOf('.', end);
                if (brk <= i) {
                    brk = text.lastIndexOf(' ', end);
                }
                if (brk > i) {
                    end = brk + 1;
                }
            }
            chunks.add(text.substring(i, end));
            i = end;
        }
        return chunks;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> lru(int max) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        });
    }
}
