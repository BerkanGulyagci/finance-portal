package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakTokenResponse;
import com.finance.portal.common.application.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider {

    private static final long REFRESH_BUFFER_SECONDS = 30;

    private final KeycloakAdminProperties properties;
    private final RestClient restClient = RestClient.create();

    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public String getAccessToken() {
        if (isTokenValid()) {
            return accessToken;
        }
        synchronized (this) {
            if (isTokenValid()) {
                return accessToken;
            }
            refreshToken();
            return accessToken;
        }
    }

    private boolean isTokenValid() {
        return accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private void refreshToken() {
        if (properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new ExternalApiException("Keycloak admin client secret yapılandırılmamış.");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());

        try {
            KeycloakTokenResponse response = restClient.post()
                    .uri(properties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new ExternalApiException("Keycloak admin token yanıtı geçersiz.");
            }

            accessToken = response.getAccessToken();
            long expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 60;
            expiresAt = Instant.now().plusSeconds(expiresIn);
            log.debug("Keycloak admin access token refreshed (expires in {}s)", expiresIn);
        } catch (RestClientResponseException ex) {
            log.error("Keycloak token request failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ExternalApiException("Keycloak admin token alınamadı.");
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Keycloak token request failed: {}", ex.getMessage());
            throw new ExternalApiException("Keycloak admin token alınamadı.");
        }
    }
}
