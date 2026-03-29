package com.finance.portal.news.application.service;

import com.finance.portal.news.infrastructure.external.NewsApiClient;
import com.finance.portal.news.infrastructure.external.dto.NewsApiResponse;
import com.finance.portal.news.infrastructure.messaging.event.NewsCacheUpdatedEvent;
import com.finance.portal.news.infrastructure.messaging.producer.NewsEventProducer;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);

    private final NewsApiClient newsApiClient;
    private final NewsEventProducer newsEventProducer;
    private final com.finance.portal.news.infrastructure.external.BloombergHtRssClient bloombergHtRssClient;

    public NewsService(NewsApiClient newsApiClient, NewsEventProducer newsEventProducer,
                       com.finance.portal.news.infrastructure.external.BloombergHtRssClient bloombergHtRssClient) {
        this.newsApiClient = newsApiClient;
        this.newsEventProducer = newsEventProducer;
        this.bloombergHtRssClient = bloombergHtRssClient;
    }

    @Cacheable(cacheNames = "newsCache", key = "#category + '_' + #country + '_' + #page + '_' + #pageSize + '_' + (#keyword != null ? #keyword : 'null')")
    public NewsResponse getNews(String category, String country, int page, int pageSize, String keyword) {
        NewsApiResponse apiResponse = newsApiClient.fetchNews(category, country, page, pageSize, keyword);

        if (apiResponse == null || apiResponse.getArticles() == null) {
            return new NewsResponse(List.of(), page, pageSize, 0, 0);
        }

        List<NewsItemDto> newsItems = apiResponse.getArticles().stream()
                .map(article -> new NewsItemDto(
                        article.getTitle(),
                        article.getDescription(),
                        article.getUrl(),
                        article.getUrlToImage(),
                        article.getPublishedAt(),
                        article.getSource() != null ? article.getSource().getName() : null,
                        article.getAuthor()
                ))
                .collect(Collectors.toList());

        int totalElements = apiResponse.getTotalResults() != null ? apiResponse.getTotalResults() : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        NewsResponse response = new NewsResponse(newsItems, page, pageSize, totalElements, totalPages);

        try {
            NewsCacheUpdatedEvent event = new NewsCacheUpdatedEvent(
                    LocalDateTime.now(),
                    totalElements
            );
            newsEventProducer.sendNewsCacheUpdatedEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish news cache updated event to Kafka: {}", e.getMessage());
        }

        return response;
    }

    @Cacheable(cacheNames = "newsCache", key = "'bloomberght'")
    public List<NewsItemDto> getBloombergHtNews() {
        return bloombergHtRssClient.fetchNews();
    }
}
