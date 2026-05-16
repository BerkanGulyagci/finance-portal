package com.finance.portal.news.application.service;

import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.port.BloombergNewsPort;
import com.finance.portal.news.application.port.NewsApiPort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final List<String> GOLD_NEWS_KEYWORDS = List.of(
            "altın", "gold", "ons", "gram altın", "çeyrek", "cumhuriyet altın",
            "kuyumcu", "külçe", "sarrafiye", "değerli metal"
    );

    private final NewsApiPort newsApiPort;
    private final BloombergNewsPort bloombergNewsPort;

    public NewsService(NewsApiPort newsApiPort, BloombergNewsPort bloombergNewsPort) {
        this.newsApiPort = newsApiPort;
        this.bloombergNewsPort = bloombergNewsPort;
    }

    @Cacheable(cacheNames = "newsCache", key = "#category + '_' + #country + '_' + #page + '_' + #pageSize + '_' + (#keyword != null ? #keyword : 'null')")
    public NewsPage getNews(String category, String country, int page, int pageSize, String keyword) {
        return newsApiPort.fetchNews(category, country, page, pageSize, keyword);
    }

    @Cacheable(cacheNames = "newsCache", key = "'bloomberght-v2'")
    public List<NewsArticle> getBloombergHtNews() {
        return bloombergNewsPort.fetchNews();
    }

    @Cacheable(cacheNames = "newsCache", key = "'gold-news-v2'")
    public GoldNewsResult getGoldNews() {
        List<NewsArticle> all = getBloombergHtNews();

        List<NewsArticle> filtered = all.stream()
                .filter(NewsService::matchesGoldKeywords)
                .limit(6)
                .collect(Collectors.toList());

        boolean isFiltered = !filtered.isEmpty();
        List<NewsArticle> result = isFiltered
                ? filtered
                : all.stream().limit(6).collect(Collectors.toList());

        String label = isFiltered ? "Altın Haberleri" : "Son Haberler";
        return new GoldNewsResult(result, isFiltered, label);
    }

    private static boolean matchesGoldKeywords(NewsArticle item) {
        String text = ((item.getTitle() != null ? item.getTitle() : "") + " "
                + (item.getDescription() != null ? item.getDescription() : "")).toLowerCase();
        return GOLD_NEWS_KEYWORDS.stream().anyMatch(text::contains);
    }
}
