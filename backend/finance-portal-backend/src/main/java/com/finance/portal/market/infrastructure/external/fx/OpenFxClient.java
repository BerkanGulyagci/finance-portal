package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OpenFxClient {

    private static final Logger log = LoggerFactory.getLogger(OpenFxClient.class);

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    @Value("${market.fx.open.base-url}")
    private String openFxBaseUrl;

    public OpenFxClient(RestTemplate restTemplate, CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_OPEN_FX,
                "fetchLatestRates",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                OpenFxClient.class.getName()
        );
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
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Open FX API: empty response body",
                        null,
                        Map.of("url", url, "base", baseCode));
                throw new ExternalApiException("Open FX API returned empty response");
            }

            if (response.getResult() == null || !"success".equalsIgnoreCase(response.getResult())) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "Open FX API: non-success result or 'result' field missing (JSON structure may have changed)",
                        null,
                        Map.of("url", url, "base", baseCode,
                                "result", String.valueOf(response.getResult())));
                throw new ExternalApiException("Open FX API returned non-success result: " + response.getResult());
            }

            // Result == success ama 'rates' alanı boş/null —
            // muhtemelen JSON yapısı değişti (sessiz hata).
            if (response.getRates() == null || response.getRates().isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "Open FX API: 'rates' map missing or empty despite success result (JSON structure may have changed)",
                        null,
                        Map.of("url", url, "base", baseCode,
                                "ratesNull", response.getRates() == null));
            }

            return response;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                        "Open FX API: rate limited (HTTP 429)",
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("url", url, "base", baseCode));
            } else {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "Open FX API client error: " + ex.getStatusCode(),
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("url", url, "base", baseCode));
            }
            throw new ExternalApiException(
                    "Open FX API returned a client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Open FX API server error: " + ex.getStatusCode(),
                    String.valueOf(ex.getStatusCode().value()),
                    Map.of("url", url, "base", baseCode));
            throw new ExternalApiException(
                    "Open FX API is currently unavailable: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access Open FX API. Please check network connectivity.", ex);
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Open FX API fetch/parse failed: " + ex.getMessage() + " (JSON structure may have changed)",
                    null,
                    Map.of("url", url, "base", baseCode,
                            "exceptionClass", ex.getClass().getSimpleName()));
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

