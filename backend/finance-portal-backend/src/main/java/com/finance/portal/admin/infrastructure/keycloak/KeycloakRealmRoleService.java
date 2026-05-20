package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakRealmRoleService {

    public static final String DEFAULT_USER_REALM_ROLE = "USER";

    private final KeycloakRealmRoleAdminClient realmRoleAdminClient;
    private final KeycloakAdminProperties adminProperties;
    private final CentralBusinessLogService centralBusinessLogService;

    /**
     * Kullanıcıda tam adıyla realm rolü yoksa atar (default-roles-* sayılmaz).
     *
     * @return atama başarılı veya rol zaten varsa true
     */
    public boolean ensureRealmRoleAssigned(String userId, String roleName) {
        if (userId == null || userId.isBlank() || roleName == null || roleName.isBlank()) {
            return false;
        }

        if (hasExactRealmRole(userId, roleName)) {
            log.info("User {} already has realm role '{}'", userId, roleName);
            publishUserRoleAssigned(userId, roleName);
            return true;
        }

        KeycloakRoleRepresentation role = resolveRealmRole(roleName);
        if (role == null || role.getId() == null || role.getId().isBlank()) {
            log.error(
                    "Failed to assign USER role to registered user {}: realm role '{}' could not be resolved "
                            + "(GET /roles/USER may return 403 — set KEYCLOAK_DEFAULT_USER_ROLE_ID in .env.local)",
                    userId,
                    roleName
            );
            return false;
        }

        return assignRealmRole(userId, role);
    }

    private void publishUserRoleAssigned(String userId, String roleName) {
        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_ROLE_ASSIGNED,
                "INFO",
                "User role assigned",
                "USER",
                userId,
                BusinessLogSupport.ACTION_ASSIGN,
                BusinessLogSupport.RESULT_SUCCESS,
                java.util.Map.of(
                        "userId", userId,
                        "roleName", roleName,
                        "success", true
                ),
                userId,
                KeycloakRealmRoleService.class.getName()
        );
    }

    public boolean hasExactRealmRole(String userId, String roleName) {
        return Arrays.stream(realmRoleAdminClient.getUserRealmRoleMappings(userId))
                .anyMatch(role -> roleName.equals(role.getName()));
    }

    private KeycloakRoleRepresentation resolveRealmRole(String roleName) {
        KeycloakRoleRepresentation fromApi = tryFetchRealmRoleByName(roleName);
        if (fromApi != null && fromApi.getId() != null && !fromApi.getId().isBlank()) {
            return fromApi;
        }

        if (DEFAULT_USER_REALM_ROLE.equals(roleName)) {
            KeycloakRoleRepresentation configured = configuredUserRole();
            if (configured != null) {
                log.info(
                        "Using configured USER role id '{}' (Admin API GET /roles/USER unavailable: check view-realm or KEYCLOAK_DEFAULT_USER_ROLE_ID)",
                        configured.getId()
                );
                return configured;
            }
        }

        return null;
    }

    private KeycloakRoleRepresentation tryFetchRealmRoleByName(String roleName) {
        try {
            return realmRoleAdminClient.getRealmRoleByName(roleName);
        } catch (ResourceNotFoundException ex) {
            log.error("Realm role '{}' not found in Keycloak", roleName);
            return null;
        } catch (Exception ex) {
            log.warn(
                    "Could not load realm role '{}' via Admin API ({}); will try configured role id if applicable",
                    roleName,
                    extractErrorDetail(ex)
            );
            return null;
        }
    }

    private KeycloakRoleRepresentation configuredUserRole() {
        String roleId = adminProperties.getDefaultUserRoleId();
        if (roleId == null || roleId.isBlank()) {
            return null;
        }
        String roleName = adminProperties.getDefaultUserRoleName();
        if (roleName == null || roleName.isBlank()) {
            roleName = DEFAULT_USER_REALM_ROLE;
        }
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId(roleId.trim());
        role.setName(roleName);
        role.setClientRole(false);
        role.setComposite(false);
        return role;
    }

    private boolean assignRealmRole(String userId, KeycloakRoleRepresentation role) {
        try {
            realmRoleAdminClient.assignRealmRolesToUser(userId, java.util.List.of(role));
            log.info("Assigned realm role '{}' to user {}", role.getName(), userId);
            publishUserRoleAssigned(userId, role.getName());
            return true;
        } catch (Exception ex) {
            if (isConflict(ex)) {
                log.info("User {} already has realm role '{}' (409 conflict ignored)", userId, role.getName());
                publishUserRoleAssigned(userId, role.getName());
                return true;
            }
            log.error(
                    "Failed to assign USER role to registered user {}: {}",
                    userId,
                    extractErrorDetail(ex)
            );
            if (isForbidden(ex)) {
                log.error(
                        "Hint: grant finance-portal-admin-service service account 'manage-users' "
                                + "(realm-management client) in Keycloak Admin Console"
                );
            }
            return false;
        }
    }

    private static boolean isConflict(Exception ex) {
        return hasStatus(ex, HttpStatus.CONFLICT);
    }

    private static boolean isForbidden(Exception ex) {
        return hasStatus(ex, HttpStatus.FORBIDDEN);
    }

    private static boolean hasStatus(Exception ex, HttpStatus status) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof RestClientResponseException response
                    && response.getStatusCode() == status) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String extractErrorDetail(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof RestClientResponseException response) {
                return "status=" + response.getStatusCode().value()
                        + " body=" + response.getResponseBodyAsString();
            }
            current = current.getCause();
        }
        if (ex instanceof ExternalApiException external) {
            return external.getMessage();
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
