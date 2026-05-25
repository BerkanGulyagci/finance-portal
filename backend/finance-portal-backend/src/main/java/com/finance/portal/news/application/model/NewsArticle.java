package com.finance.portal.news.application.model;

/**
 * Haber kalemi — presentation DTO'larından bağımsız application modeli.
 */
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

    public NewsArticle(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author, String category,
                       String language, String content) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
        this.source = source;
        this.author = author;
        this.category = category;
        this.language = language;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getSource() {
        return source;
    }

    public String getAuthor() {
        return author;
    }

    public String getCategory() {
        return category;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
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
