package com.finance.portal.news.application.model;

import java.util.List;

/** Altın haberleri endpoint'i için filtrelenmiş veya yedek liste sonucu. */
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

    public List<NewsArticle> getItems() {
        return items;
    }

    public boolean isFiltered() {
        return filtered;
    }

    public String getLabel() {
        return label;
    }
}
