package com.finance.portal.auth.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.auth.application.port.KeycloakPasswordVerifierPort;
import com.finance.portal.auth.infrastructure.keycloak.dto.KeycloakTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakPasswordVerifierAdapter implements KeycloakPasswordVerifierPort {

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakPortalProperties portalProperties;
    private final RestClient restClient = RestClient.create();

    @Override
    public boolean verifyCurrentPassword(String username, String currentPassword) {
        if (username == null || username.isBlank() || currentPassword == null || currentPassword.isBlank()) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", portalProperties.getPublicClientId());
        form.add("username", username.trim());
        form.add("password", currentPassword);

        try {
            KeycloakTokenResponse response = restClient.post()
                    .uri(adminProperties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
            return response != null && response.getAccessToken() != null && !response.getAccessToken().isBlank();
        } catch (RestClientResponseException ex) {
            log.debug("Password verification failed for user '{}': status={}", username, ex.getStatusCode());
            return false;
        } catch (Exception ex) {
            log.warn("Password verification error for user '{}': {}", username, ex.getMessage());
            return false;
        }
    }
}
