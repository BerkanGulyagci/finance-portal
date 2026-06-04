package com.finance.portal.news.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Haber kalemi — presentation DTO'larından bağımsız application modeli.
 */
@Getter
@AllArgsConstructor
public class NewsArticle {

    private final String title;
    private final String description;
    private final String url;
    private final String imageUrl;
    private final String publishedAt;
    private final String source;
    private final String author;
    /** Normalize edilmiş kategori adı (NewsCategory.name()); aggregator tarafından atanır. */
    private final String category;
    /** Dil/bölge: "tr" (Türkiye) veya "en" (global). Türkiye/Global filtresi için. */
    private final String language;
    /**
     * RSS {@code <content:encoded>} ile gelen tam içerik (ham HTML). Varsa detay sayfasında
     * makale sayfasını scrape etmeden kullanılır (kriptofoni/ekonomimtv gibi kaynaklar).
     */
    private final String content;

    /** Jackson / Redis cache deserialization */
    public NewsArticle() {
        this(null, null, null, null, null, null, null, null, null, null);
    }

    public NewsArticle(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author) {
        this(title, description, url, imageUrl, publishedAt, source, author, null, null, null);
    }

    public NewsArticle(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author, String category) {
        this(title, description, url, imageUrl, publishedAt, source, author, category, null, null);
    }

    public NewsArticle(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author, String category, String language) {
        this(title, description, url, imageUrl, publishedAt, source, author, category, language, null);
    }

    public NewsArticle withImageUrl(String newImageUrl) {
        return new NewsArticle(title, description, url, newImageUrl, publishedAt, source, author, category, language, content);
    }

    public NewsArticle withCategory(String newCategory) {
        return new NewsArticle(title, description, url, imageUrl, publishedAt, source, author, newCategory, language, content);
    }

    public NewsArticle withLanguage(String newLanguage) {
        return new NewsArticle(title, description, url, imageUrl, publishedAt, source, author, category, newLanguage, content);
    }

    public NewsArticle withContent(String newContent) {
        return new NewsArticle(title, description, url, imageUrl, publishedAt, source, author, category, language, newContent);
    }

    /** Çeviri için: başlık + özeti değiştirir (diğer alanlar korunur). */
    public NewsArticle withText(String newTitle, String newDescription) {
        return new NewsArticle(newTitle, newDescription, url, imageUrl, publishedAt, source, author, category, language, content);
    }
}
