package com.finance.portal.news.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Haber kaynakları yapılandırması (application.yml: news.sources).
 * RSS feed listesi anahtarsız; API anahtarları boşsa o sağlayıcı devre dışı kalır.
 */
@Component
@ConfigurationProperties(prefix = "news.sources")
public class NewsSourcesProperties {

    /** RSS feed listesi. */
    private List<RssFeed> rss = new ArrayList<>();

    public List<RssFeed> getRss() {
        return rss;
    }

    public void setRss(List<RssFeed> rss) {
        this.rss = rss;
    }

    /** Tek bir RSS feed tanımı. */
    public static class RssFeed {
        /** Görünen kaynak adı (article.source + filtre için). */
        private String name;
        /** Feed URL. */
        private String url;
        /** Varsayılan kategori (NewsCategory adı); classifier daha spesifik bulamazsa kullanılır. */
        private String category = "ECONOMY";
        /** Dil (tr/en) — filtre için. */
        private String language = "tr";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }
}
