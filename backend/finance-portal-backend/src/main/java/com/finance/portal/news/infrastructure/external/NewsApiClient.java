package com.finance.portal.news.infrastructure.external;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.port.NewsApiPort;
import com.finance.portal.news.infrastructure.external.dto.NewsApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class NewsApiClient implements NewsApiPort {

    private static final Logger log = LoggerFactory.getLogger(NewsApiClient.class);

    private final RestTemplate restTemplate;

    @Value("${news.api.url}")
    private String newsApiUrl;

    @Value("${news.api.key}")
    private String newsApiKey;

    public NewsApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public NewsPage fetchNews(String category, String country, int page, int pageSize, String keyword) {
        NewsApiResponse apiResponse = fetchFromApi(category, country, page, pageSize, keyword);
        return toNewsPage(apiResponse, page, pageSize);
    }

    private NewsApiResponse fetchFromApi(String category, String country, int page, int pageSize, String keyword) {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(newsApiUrl)
                .queryParam("apiKey", newsApiKey)
                .queryParam("category", category)
                .queryParam("country", country)
                .queryParam("page", page)
                .queryParam("pageSize", pageSize);

        if (keyword != null && !keyword.trim().isEmpty()) {
            uriBuilder.queryParam("q", keyword);
        }

        String url = uriBuilder.toUriString();
        log.debug("Fetching news from external API: {}", url);

        try {
            ResponseEntity<NewsApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    NewsApiResponse.class
            );

            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw new ExternalApiException(
                    "External news API returned a client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new ExternalApiException(
                    "External news API is currently unavailable: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access external news API. Please check network connectivity.", ex);
        }
    }

    private static NewsPage toNewsPage(NewsApiResponse apiResponse, int page, int pageSize) {
        if (apiResponse == null || apiResponse.getArticles() == null) {
            return new NewsPage(List.of(), page, pageSize, 0, 0);
        }

        List<NewsArticle> articles = apiResponse.getArticles().stream()
                .map(article -> new NewsArticle(
                        article.getTitle(),
                        article.getDescription(),
                        article.getUrl(),
                        article.getUrlToImage(),
                        article.getPublishedAt(),
                        article.getSource() != null ? article.getSource().getName() : null,
                        article.getAuthor()))
                .collect(Collectors.toList());

        int totalElements = apiResponse.getTotalResults() != null ? apiResponse.getTotalResults() : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new NewsPage(articles, page, pageSize, totalElements, totalPages);
    }
}
