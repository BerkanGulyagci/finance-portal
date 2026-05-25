package com.finance.portal.news.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.port.NewsProvider;
import com.finance.portal.news.domain.NewsDateUtil;
import com.finance.portal.news.domain.NewsHtmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finnhub piyasa haberleri (ücretsiz). Anahtar yoksa devre dışı.
 * "general" + "crypto" + "forex" kategorilerini çeker; classifier ile normalize edilir.
 */
@Component
public class FinnhubNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubNewsProvider.class);
    private static final List<String> CATEGORIES = List.of("general", "crypto", "forex");

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;
    private final String url;
    private final String apiKey;

    public FinnhubNewsProvider(RestTemplate restTemplate,
                               CentralIntegrationLogService integrationLog,
                               @Value("${news.sources.finnhub.url:https://finnhub.io/api/v1/news}") String url,
                               @Value("${news.sources.finnhub.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public String id() {
        return "finnhub";
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<NewsArticle> fetch() {
        if (!isEnabled()) {
            return List.of();
        }
        List<NewsArticle> out = new ArrayList<>();
        for (String category : CATEGORIES) {
            try {
                String requestUrl = UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("category", category)
                        .queryParam("token", apiKey)
                        .build(true).toUriString();
                JsonNode arr = restTemplate.getForObject(requestUrl, JsonNode.class);
                if (arr == null || !arr.isArray()) {
                    continue;
                }
                String defaultCat = switch (category) {
                    case "crypto" -> "CRYPTO";
                    case "forex" -> "FX";
                    default -> "WORLD";
                };
                for (JsonNode n : arr) {
                    String title = text(n, "headline");
                    String link = text(n, "url");
                    if (title == null || link == null) {
                        continue;
                    }
                    long dt = n.has("datetime") ? n.get("datetime").asLong(0) : 0;
                    // summary bazen ham HTML (<p>/<ul>/<li>) içerir → düz metne çevir (lead'de etiket görünmesin).
                    String summary = NewsHtmlUtil.stripToText(text(n, "summary"), 600);
                    out.add(new NewsArticle(
                            title, summary, link, text(n, "image"),
                            dt > 0 ? NewsDateUtil.fromEpochSeconds(dt) : null,
                            "Finnhub", text(n, "source"), defaultCat, "en"));
                }
            } catch (Exception e) {
                log.warn("Finnhub fetch failed [{}]: {}", category, e.getMessage());
                integrationLog.publish(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED, "WARN",
                        "Finnhub fetch failed: " + category, "finnhub", "fetch", null, null, null, Boolean.TRUE,
                        Map.of("category", category, "error", String.valueOf(e.getMessage())),
                        FinnhubNewsProvider.class.getName());
            }
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
