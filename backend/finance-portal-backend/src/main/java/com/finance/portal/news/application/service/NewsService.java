package com.finance.portal.news.application.service;

import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.port.BloombergNewsPort;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

    private final BloombergNewsPort bloombergNewsPort;

    public NewsService(BloombergNewsPort bloombergNewsPort) {
        this.bloombergNewsPort = bloombergNewsPort;
    }

    @Cacheable(cacheNames = "newsCache", key = "'bloomberght-v2'")
    @WithSpan("NewsService.getBloombergHtNews")
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
