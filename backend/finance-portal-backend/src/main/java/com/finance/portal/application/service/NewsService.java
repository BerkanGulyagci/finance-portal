package com.finance.portal.application.service;

import com.finance.portal.infrastructure.external.NewsApiClient;
import com.finance.portal.infrastructure.external.dto.NewsApiResponse;
import com.finance.portal.presentation.dto.NewsItemDto;
import com.finance.portal.presentation.dto.NewsResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private final NewsApiClient newsApiClient;

    public NewsService(NewsApiClient newsApiClient) {
        this.newsApiClient = newsApiClient;
    }

    @Cacheable(cacheNames = "newsCache", key = "'latestNews'")
    public NewsResponse getNews() {
        NewsApiResponse apiResponse = newsApiClient.fetchNews();

        if (apiResponse == null || apiResponse.getArticles() == null) {
            return new NewsResponse(List.of(), 0);
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

        return new NewsResponse(newsItems, apiResponse.getTotalResults());
    }
}
