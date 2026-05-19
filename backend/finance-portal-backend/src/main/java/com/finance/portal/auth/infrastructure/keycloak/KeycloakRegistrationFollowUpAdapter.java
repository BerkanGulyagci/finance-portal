package com.finance.portal.auth.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminRestClient;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakUserRepresentation;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakRegistrationFollowUpAdapter implements KeycloakRegistrationFollowUpPort {

    private static final String VERIFY_EMAIL_ACTION = "VERIFY_EMAIL";
    private static final int MAX_LOOKUP_ATTEMPTS = 5;
    private static final long LOOKUP_DELAY_MS = 2_000L;

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakPortalProperties portalProperties;
    private final KeycloakAdminRestClient restClient;

    @Override
    public void requestEmailVerificationIfUserExists(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        Optional<KeycloakUserRepresentation> user = findUserWithRetry(username.trim());
        if (user.isEmpty()) {
            log.info(
                    "Keycloak user '{}' not found after LDAP register; VERIFY_EMAIL will apply on first login.",
                    username
            );
            return;
        }

        String userId = user.get().getId();
        try {
            sendVerifyEmail(userId);
            log.info("VERIFY_EMAIL requested for Keycloak user '{}' ({})", username, userId);
        } catch (Exception ex) {
            log.warn("Could not send VERIFY_EMAIL for user '{}': {}", username, ex.getMessage());
        }
    }

    private Optional<KeycloakUserRepresentation> findUserWithRetry(String username) {
        for (int attempt = 1; attempt <= MAX_LOOKUP_ATTEMPTS; attempt++) {
            Optional<KeycloakUserRepresentation> user = findUserByUsername(username);
            if (user.isPresent()) {
                return user;
            }
            if (attempt < MAX_LOOKUP_ATTEMPTS) {
                sleepQuietly(LOOKUP_DELAY_MS);
            }
        }
        return Optional.empty();
    }

    private Optional<KeycloakUserRepresentation> findUserByUsername(String username) {
        String url = UriComponentsBuilder.fromHttpUrl(adminProperties.adminApiBase() + "/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .toUriString();

        KeycloakUserRepresentation[] users = restClient.get(url, KeycloakUserRepresentation[].class);
        if (users == null || users.length == 0) {
            return Optional.empty();
        }
        return Optional.of(users[0]);
    }

    private void sendVerifyEmail(String userId) {
        String url = adminProperties.adminApiBase() + "/users/" + userId + "/execute-actions-email";
        Map<String, String> query = Map.of(
                "client_id", portalProperties.getPublicClientId(),
                "redirect_uri", portalProperties.getPostVerifyRedirectUri()
        );
        restClient.putWithQuery(url, List.of(VERIFY_EMAIL_ACTION), query);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
