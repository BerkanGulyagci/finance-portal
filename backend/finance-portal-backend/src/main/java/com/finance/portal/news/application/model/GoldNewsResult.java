package com.finance.portal.news.application.model;

import lombok.Getter;

import java.util.List;

/** Altın haberleri endpoint'i için filtrelenmiş veya yedek liste sonucu. */
@Getter
public class GoldNewsResult {

    private final List<NewsArticle> items;
    private final boolean filtered;
    private final String label;

    public GoldNewsResult() {
        this(List.of(), false, "Son Haberler");
    }

    public GoldNewsResult(List<NewsArticle> items, boolean filtered, String label) {
        this.items = items != null ? List.copyOf(items) : List.of();
        this.filtered = filtered;
        this.label = label;
    }
}
