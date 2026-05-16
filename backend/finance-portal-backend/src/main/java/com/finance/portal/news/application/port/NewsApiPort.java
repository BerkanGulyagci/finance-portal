package com.finance.portal.news.application.port;

import com.finance.portal.news.application.model.NewsPage;

/**
 * Harici NewsAPI adaptör portu (infrastructure → application sınırı).
 */
public interface NewsApiPort {

    NewsPage fetchNews(String category, String country, int page, int pageSize, String keyword);
}
