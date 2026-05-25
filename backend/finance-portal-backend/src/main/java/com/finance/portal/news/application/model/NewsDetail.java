package com.finance.portal.news.application.model;

import java.util.List;

/**
 * Haber detayı: makale + aynı kategoriden ilgili haberler ("Bunlar da İlginizi Çekebilir").
 */
public record NewsDetail(
        NewsArticle article,
        List<NewsArticle> related,
        String content
) {
}
