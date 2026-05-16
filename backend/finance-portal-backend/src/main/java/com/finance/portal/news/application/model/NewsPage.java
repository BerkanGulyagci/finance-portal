package com.finance.portal.news.application.model;

import java.util.List;

/** Sayfalanmış haber listesi (NewsAPI kaynağı). */
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

    public List<NewsArticle> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
