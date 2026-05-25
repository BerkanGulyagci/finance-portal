package com.finance.portal.news.presentation.dto;

import java.util.List;

/**
 * Haber detay yanıtı: makale + ilgili haberler.
 */
public class NewsDetailResponse {

    private NewsItemDto article;
    private List<NewsItemDto> related;
    /** Makale tam metni (best-effort); çıkarılamazsa null → frontend özete düşer. */
    private String content;

    public NewsDetailResponse() {
    }

    public NewsDetailResponse(NewsItemDto article, List<NewsItemDto> related, String content) {
        this.article = article;
        this.related = related;
        this.content = content;
    }

    public NewsItemDto getArticle() {
        return article;
    }

    public List<NewsItemDto> getRelated() {
        return related;
    }

    public String getContent() {
        return content;
    }
}
