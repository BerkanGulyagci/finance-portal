package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakUserRepresentation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakUserAdminAdapter implements KeycloakUserAdminPort {

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminRestClient restClient;

    @Override
    public List<AdminUserView> listUsers(String search, int first, int max) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.adminApiBase() + "/users")
                .queryParam("first", first)
                .queryParam("max", max)
                .queryParamIfPresent("search", search == null ? Optional.empty() : Optional.of(search))
                .toUriString();

        KeycloakUserRepresentation[] users = restClient.get(url, KeycloakUserRepresentation[].class);
        if (users == null || users.length == 0) {
            return Collections.emptyList();
        }

        List<AdminUserView> result = new ArrayList<>(users.length);
        for (KeycloakUserRepresentation user : users) {
            List<String> roles = fetchRealmRoleNamesSafely(user.getId());
            result.add(toView(user, roles));
        }
        return result;
    }

    @Override
    @WithSpan("KeycloakUserAdminAdapter.findUsersByRealmRole")
    public List<AdminUserView> findUsersByRealmRole(@SpanAttribute("keycloak.role") String roleName) {
        // 1) Verimli yol: GET /roles/{role}/users — ANCAK bu uç "view-realm" yetkisi ister.
        //    Servis hesabımızda yalnız "view-users" var; bu nedenle çoğu kurulumda 403 döner.
        try {
            String url = properties.adminApiBase() + "/roles/" + roleName + "/users?max=100";
            KeycloakUserRepresentation[] users = restClient.get(url, KeycloakUserRepresentation[].class);
            if (users != null && users.length > 0) {
                List<AdminUserView> result = new ArrayList<>(users.length);
                for (KeycloakUserRepresentation user : users) {
                    result.add(toView(user, Collections.emptyList()));
                }
                Span.current().setAttribute("keycloak.lookup_path", "role-members");
                Span.current().setAttribute("keycloak.user_count", result.size());
                return result;
            }
        } catch (Exception ex) {
            log.debug("roles/{}/users erişilemedi ({}); kullanıcı taramasına geçiliyor", roleName, ex.getMessage());
        }
        // 2) Yedek yol: tüm kullanıcıları listele, realm rol eşlemesinde {roleName} olanları süz.
        //    Yalnız "view-users" gerektirir (listUsers + /users/{id}/role-mappings/realm), bu yüzden 403 vermez.
        try {
            List<AdminUserView> result = listUsers(null, 0, 100).stream()
                    .filter(u -> u.getRoles() != null && u.getRoles().contains(roleName))
                    .toList();
            Span.current().setAttribute("keycloak.lookup_path", "user-scan");
            Span.current().setAttribute("keycloak.user_count", result.size());
            return result;
        } catch (Exception ex) {
            Span.current().setAttribute("keycloak.lookup_path", "failed");
            log.warn("Realm rolüne sahip kullanıcılar yüklenemedi [{}]: {}", roleName, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public AdminUserView getUser(String userId) {
        KeycloakUserRepresentation user = restClient.get(userUrl(userId), KeycloakUserRepresentation.class);
        return toView(user, fetchRealmRoleNamesSafely(userId));
    }

    @Override
    public void setUserEnabled(String userId, boolean enabled) {
        KeycloakUserRepresentation user = restClient.get(userUrl(userId), KeycloakUserRepresentation.class);
        user.setEnabled(enabled);
        restClient.put(userUrl(userId), user);
    }

    @Override
    public void logoutUserSessions(String userId) {
        try {
            restClient.post(properties.adminApiBase() + "/users/" + userId + "/logout");
        } catch (Exception ex) {
            log.warn("Could not logout Keycloak sessions for user {}: {}", userId, ex.getMessage());
        }
    }

    private String userUrl(String userId) {
        return properties.adminApiBase() + "/users/" + userId;
    }

    private List<String> fetchRealmRoleNamesSafely(String userId) {
        try {
            KeycloakRoleRepresentation[] roles = restClient.get(
                    userUrl(userId) + "/role-mappings/realm",
                    KeycloakRoleRepresentation[].class
            );
            return mapRoleNames(roles);
        } catch (Exception ex) {
            log.warn("Realm roles could not be loaded for user {}: {}", userId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<String> mapRoleNames(KeycloakRoleRepresentation[] roles) {
        if (roles == null || roles.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles)
                .map(KeycloakRoleRepresentation::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }

    private static AdminUserView toView(KeycloakUserRepresentation user, List<String> roles) {
        boolean enabled = Boolean.TRUE.equals(user.getEnabled());
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                enabled,
                roles,
                null,
                false,
                enabled ? BanStatus.ACTIVE : BanStatus.PERMANENT_BANNED,
                null
        );
    }
}
