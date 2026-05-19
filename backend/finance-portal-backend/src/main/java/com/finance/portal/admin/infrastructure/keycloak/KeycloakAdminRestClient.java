package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminRestClient {

    private final KeycloakAdminTokenProvider tokenProvider;
    private final RestClient restClient = RestClient.create();

    public <T> T get(String path, Class<T> responseType) {
        return exchange(() -> restClient.get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                .retrieve()
                .body(responseType));
    }

    public void put(String path, Object body) {
        exchange(() -> {
            restClient.put()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public void post(String path) {
        post(path, null);
    }

    public void post(String path, Object body) {
        exchange(() -> {
            var request = restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.body(body);
            }
            request.retrieve().toBodilessEntity();
            return null;
        });
    }

    public void putWithQuery(String path, Object body, Map<String, String> queryParams) {
        String uri = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(path);
            queryParams.forEach(builder::queryParam);
            uri = builder.toUriString();
        }
        String finalUri = uri;
        exchange(() -> {
            restClient.put()
                    .uri(finalUri)
                    .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T exchange(RestClientCall<T> call) {
        try {
            return call.execute();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Kullanıcı bulunamadı.");
            }
            String body = ex.getResponseBodyAsString();
            log.error("Keycloak Admin API error: status={}, body={}", ex.getStatusCode(), body);
            throw new ExternalApiException(
                    "Keycloak Admin API isteği başarısız oldu: status="
                            + ex.getStatusCode().value()
                            + " body="
                            + body
            );
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Keycloak Admin API error: {}", ex.getMessage());
            throw new ExternalApiException("Keycloak Admin API isteği başarısız oldu.");
        }
    }

    @FunctionalInterface
    private interface RestClientCall<T> {
        T execute();
    }
}
