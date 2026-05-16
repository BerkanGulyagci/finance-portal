package com.finance.portal.news.application.port;

import com.finance.portal.news.application.model.NewsArticle;

import java.util.List;

/**
 * BloombergHT RSS adaptör portu.
 */
public interface BloombergNewsPort {

    List<NewsArticle> fetchNews();
}
