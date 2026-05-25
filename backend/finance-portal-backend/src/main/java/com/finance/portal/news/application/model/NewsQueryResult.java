package com.finance.portal.news.application.model;

import java.util.List;
import java.util.Map;

/**
 * Filtrelenmiş + sayfalanmış haber sonucu, filtre UI için facet bilgisiyle birlikte.
 */
public record NewsQueryResult(
        List<NewsArticle> items,
        int page,
        int pageSize,
        long totalElements,
        int totalPages,
        List<String> sources,
        Map<String, Long> categoryCounts
) {
}
