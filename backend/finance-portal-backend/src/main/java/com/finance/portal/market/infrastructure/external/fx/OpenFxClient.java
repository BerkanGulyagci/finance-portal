package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class OpenFxClient {

    private static final Logger log = LoggerFactory.getLogger(OpenFxClient.class);

    private final RestTemplate restTemplate;

    @Value("${market.fx.open.base-url}")
    private String openFxBaseUrl;

    public OpenFxClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public OpenErApiResponseDto fetchLatestRates(String base) {
        String baseCode = (base != null && !base.trim().isEmpty())
                ? base.trim().toUpperCase()
                : "USD";

        String url = buildUrl(baseCode);
        log.debug("Fetching latest FX rates from Open ER API: {}", url);

        try {
            OpenErApiResponseDto response = restTemplate.getForObject(url, OpenErApiResponseDto.class);

            if (response == null) {
                throw new ExternalApiException("Open FX API returned empty response");
            }

            if (response.getResult() == null || !"success".equalsIgnoreCase(response.getResult())) {
                throw new ExternalApiException("Open FX API returned non-success result: " + response.getResult());
            }

            return response;
        } catch (HttpClientErrorException ex) {
            throw new ExternalApiException(
                    "Open FX API returned a client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new ExternalApiException(
                    "Open FX API is currently unavailable: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access Open FX API. Please check network connectivity.", ex);
        } catch (Exception ex) {
            throw new ExternalApiException(
                    "Failed to parse Open FX API response", ex);
        }
    }

    private String buildUrl(String baseCode) {
        String baseUrl = openFxBaseUrl != null ? openFxBaseUrl.trim() : "";
        if (baseUrl.endsWith("/")) {
            return baseUrl + baseCode;
        }
        return baseUrl + "/" + baseCode;
    }
}

