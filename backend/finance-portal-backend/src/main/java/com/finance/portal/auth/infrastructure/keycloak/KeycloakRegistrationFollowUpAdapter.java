package com.finance.portal.auth.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminRestClient;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleService;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakUserRepresentation;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
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
    private static final int MAX_LOOKUP_ATTEMPTS = 15;
    private static final long LOOKUP_DELAY_MS = 3_000L;

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakPortalProperties portalProperties;
    private final KeycloakAdminRestClient restClient;
    private final KeycloakRealmRoleService keycloakRealmRoleService;
    private final CentralBusinessLogService centralBusinessLogService;

    @Override
    public void requestEmailVerificationForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            sendVerifyEmail(userId);
            log.info("VERIFY_EMAIL requested for Keycloak user id {}", userId);

            centralBusinessLogService.publish(
                    BusinessLogSupport.CATEGORY_AUDIT,
                    BusinessLogSupport.EVENT_EMAIL_VERIFICATION_REQUESTED,
                    "INFO",
                    "Email verification requested",
                    "USER",
                    userId,
                    BusinessLogSupport.ACTION_CREATE,
                    BusinessLogSupport.RESULT_SUCCESS,
                    Map.of(
                            "keycloakUserId", userId,
                            "outcome", BusinessLogSupport.RESULT_SUCCESS
                    ),
                    userId,
                    KeycloakRegistrationFollowUpAdapter.class.getName()
            );
        } catch (Exception ex) {
            log.warn("Could not send VERIFY_EMAIL for user id '{}': {}", userId, ex.getMessage());
        }
    }

    @Override
    public void requestEmailVerificationIfUserExists(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String normalizedUsername = username.trim();
        log.info("Registration follow-up started for username '{}'", normalizedUsername);

        Optional<KeycloakUserRepresentation> user = findUserWithRetry(normalizedUsername);
        if (user.isEmpty()) {
            log.warn(
                    "Keycloak user '{}' not found after LDAP register ({} attempts); "
                            + "USER role and VERIFY_EMAIL will be retried on first /api/v1/me or first login.",
                    normalizedUsername,
                    MAX_LOOKUP_ATTEMPTS
            );
            return;
        }

        String userId = user.get().getId();
        log.info("Keycloak user '{}' found (id={}) for registration follow-up", normalizedUsername, userId);

        boolean userRoleAssigned = keycloakRealmRoleService.ensureRealmRoleAssigned(
                userId,
                KeycloakRealmRoleService.DEFAULT_USER_REALM_ROLE
        );
        if (!userRoleAssigned) {
            log.warn(
                    "USER role assignment did not complete for user '{}' (id={}); will retry on /api/v1/me",
                    normalizedUsername,
                    userId
            );
        }
        // E-posta doğrulaması KASITLI olarak burada tetiklenmez.
        // Realm `verifyEmail:true` ayarı, kullanıcı ilk login olduğunda standart
        // VERIFY_EMAIL akışını çalıştırır ve doğru host'ta (/login-actions) link üreten
        // "Verify email" e-postasını gönderir. Buradan ayrıca execute-actions-email
        // çağrısı yapmak; (1) ikinci, gereksiz bir "Update Your Account" e-postası üretir,
        // (2) admin API host'una (port 8080) göre yanlış action-token linki ürettiğinden
        // kullanıcıyı kırık bir adrese yönlendirirdi. Bkz. updateEmail akışı:
        // e-posta DEĞİŞİMİNDE doğrulama hâlâ requestEmailVerificationForUser ile tetiklenir.
    }

    private Optional<KeycloakUserRepresentation> findUserWithRetry(String username) {
        for (int attempt = 1; attempt <= MAX_LOOKUP_ATTEMPTS; attempt++) {
            Optional<KeycloakUserRepresentation> user = findUserByUsername(username);
            if (user.isPresent()) {
                log.info("Keycloak user '{}' located on attempt {}/{}", username, attempt, MAX_LOOKUP_ATTEMPTS);
                return user;
            }
            log.debug(
                    "Keycloak user '{}' not found yet (attempt {}/{})",
                    username,
                    attempt,
                    MAX_LOOKUP_ATTEMPTS
            );
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
