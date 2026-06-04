package com.finance.portal.news.application.model;

import lombok.Getter;

import java.util.List;

/** Sayfalanmış haber listesi (NewsAPI kaynağı). */
@Getter
public class NewsPage {

    private final List<NewsArticle> items;
    private final int page;
    private final int pageSize;
    private final int totalElements;
    private final int totalPages;

    public NewsPage() {
        this(List.of(), 1, 10, 0, 0);
    }

    public NewsPage(List<NewsArticle> items, int page, int pageSize, int totalElements, int totalPages) {
        this.items = items != null ? List.copyOf(items) : List.of();
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}
