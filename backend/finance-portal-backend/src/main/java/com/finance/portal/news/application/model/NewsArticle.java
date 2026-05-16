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

    /** Jackson / Redis cache deserialization */
    public NewsArticle() {
        this(null, null, null, null, null, null, null);
    }

    public NewsArticle(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
        this.source = source;
        this.author = author;
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

    public NewsArticle withImageUrl(String newImageUrl) {
        return new NewsArticle(title, description, url, newImageUrl, publishedAt, source, author);
    }
}
