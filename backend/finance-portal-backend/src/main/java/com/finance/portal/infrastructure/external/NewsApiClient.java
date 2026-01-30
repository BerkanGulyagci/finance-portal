package com.finance.portal.infrastructure.external;

import com.finance.portal.infrastructure.exception.ExternalApiException;
import com.finance.portal.infrastructure.external.dto.NewsApiResponse;
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

@Component
public class NewsApiClient {

    private final RestTemplate restTemplate;

    @Value("${news.api.url}")
    private String newsApiUrl;

    @Value("${news.api.key}")
    private String newsApiKey;

    public NewsApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public NewsApiResponse fetchNews() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(newsApiUrl)
                .queryParam("apiKey", newsApiKey);

        try {
            ResponseEntity<NewsApiResponse> response = restTemplate.exchange(
                    uriBuilder.toUriString(),
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
}

