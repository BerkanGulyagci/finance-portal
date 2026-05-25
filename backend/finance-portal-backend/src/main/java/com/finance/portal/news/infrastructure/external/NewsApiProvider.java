package com.finance.portal.news.infrastructure.external;

import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.port.NewsApiPort;
import com.finance.portal.news.application.port.NewsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mevcut newsapi.org istemcisini ({@link NewsApiPort}) aggregator'a sağlayıcı olarak bağlar.
 * Anahtar tanımlı değilse devre dışı.
 */
@Component
public class NewsApiProvider implements NewsProvider {

    private final NewsApiPort newsApiPort;
    private final String apiKey;

    public NewsApiProvider(NewsApiPort newsApiPort,
                           @Value("${news.api.key:}") String apiKey) {
        this.newsApiPort = newsApiPort;
        this.apiKey = apiKey;
    }

    @Override
    public String id() {
        return "newsapi";
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
        try {
            NewsPage page = newsApiPort.fetchNews("business", "tr", 1, 50, null);
            if (page == null || page.getItems() == null) {
                return List.of();
            }
            // newsapi.org country=tr → Türkçe/Türkiye haberi olarak işaretle
            return page.getItems().stream().map(a -> a.withLanguage("tr")).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
