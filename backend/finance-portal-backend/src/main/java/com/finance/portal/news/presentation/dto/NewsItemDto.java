package com.finance.portal.news.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NewsItemDto {

    /** URL'den türetilmiş kararlı kimlik (detay sayfası linki). */
    private String id;
    private String title;
    private String description;
    private String url;
    private String imageUrl;
    private String publishedAt;
    private String source;
    private String author;
    /** Normalize kategori adı (NewsCategory.name()) + görünen etiket. */
    private String category;
    private String categoryLabel;

    public NewsItemDto(String title, String description, String url, String imageUrl,
                       String publishedAt, String source, String author) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
        this.source = source;
        this.author = author;
    }
}
