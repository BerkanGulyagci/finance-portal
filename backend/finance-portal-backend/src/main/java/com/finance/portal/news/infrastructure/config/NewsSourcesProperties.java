package com.finance.portal.news.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Haber kaynakları yapılandırması (application.yml: news.sources).
 * RSS feed listesi anahtarsız; API anahtarları boşsa o sağlayıcı devre dışı kalır.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "news.sources")
public class NewsSourcesProperties {

    /** RSS feed listesi. */
    private List<RssFeed> rss = new ArrayList<>();

    /** Tek bir RSS feed tanımı. */
    @Getter
    @Setter
    public static class RssFeed {
        /** Görünen kaynak adı (article.source + filtre için). */
        private String name;
        /** Feed URL. */
        private String url;
        /** Varsayılan kategori (NewsCategory adı); classifier daha spesifik bulamazsa kullanılır. */
        private String category = "ECONOMY";
        /** Dil (tr/en) — filtre için. */
        private String language = "tr";
    }
}
