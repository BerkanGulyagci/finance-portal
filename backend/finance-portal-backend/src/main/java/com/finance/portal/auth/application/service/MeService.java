package com.finance.portal.auth.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleService;
import com.finance.portal.auth.presentation.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeService {

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final KeycloakRealmRoleService keycloakRealmRoleService;

    public MeResponse getCurrentUser(Jwt jwt) {
        String userId = jwt.getSubject();
        if (userId != null && !userId.isBlank()) {
            try {
                AdminUserView user = keycloakUserAdminPort.getUser(userId);
                user = ensureUserRealmRoleAndRefresh(userId, user);
                return toResponse(user);
            } catch (Exception ex) {
                log.warn(
                        "Could not load user '{}' from Keycloak; falling back to JWT claims: {}",
                        userId,
                        ex.getMessage()
                );
            }
        }
        return fromJwt(jwt);
    }

    private AdminUserView ensureUserRealmRoleAndRefresh(String userId, AdminUserView user) {
        if (keycloakRealmRoleService.hasExactRealmRole(userId, KeycloakRealmRoleService.DEFAULT_USER_REALM_ROLE)) {
            return user;
        }
        log.info("User {} missing realm role USER; attempting assignment via /api/me", userId);
        keycloakRealmRoleService.ensureRealmRoleAssigned(userId, KeycloakRealmRoleService.DEFAULT_USER_REALM_ROLE);
        return keycloakUserAdminPort.getUser(userId);
    }

    private static MeResponse toResponse(AdminUserView user) {
        MeResponse response = new MeResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmailVerified(user.isEmailVerified());
        response.setRoles(user.getRoles() != null ? user.getRoles() : Collections.emptyList());
        response.setEnabled(user.isEnabled());
        return response;
    }

    private static MeResponse fromJwt(Jwt jwt) {
        MeResponse response = new MeResponse();
        response.setId(jwt.getSubject());
        response.setUsername(readStringClaim(jwt, "preferred_username", "username"));
        response.setEmail(jwt.getClaimAsString("email"));
        response.setFirstName(jwt.getClaimAsString("given_name"));
        response.setLastName(jwt.getClaimAsString("family_name"));
        response.setEmailVerified(isEmailVerified(jwt));
        response.setRoles(extractRealmRoles(jwt));
        response.setEnabled(true);
        return response;
    }

    private static boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        if (claim instanceof Boolean bool) {
            return bool;
        }
        if (claim instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return false;
    }

    private static String readStringClaim(Jwt jwt, String... keys) {
        for (String key : keys) {
            String value = jwt.getClaimAsString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> extractRealmRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Collections.emptyList();
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> !role.isBlank())
                .sorted()
                .toList();
    }
}
