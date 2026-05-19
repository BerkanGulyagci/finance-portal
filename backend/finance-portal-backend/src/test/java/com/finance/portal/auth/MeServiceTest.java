package com.finance.portal.auth;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleService;
import com.finance.portal.auth.application.service.MeService;
import com.finance.portal.auth.presentation.dto.MeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    KeycloakUserAdminPort keycloakUserAdminPort;

    @Mock
    KeycloakRealmRoleService keycloakRealmRoleService;

    @InjectMocks
    MeService meService;

    @Test
    void shouldReturnKeycloakProfileWhenAvailable() {
        when(keycloakRealmRoleService.hasExactRealmRole("kc-id", "USER")).thenReturn(true);
        when(keycloakUserAdminPort.getUser("kc-id")).thenReturn(
                new AdminUserView(
                        "kc-id", "alice", "alice@example.com", "Ali", "Veli",
                        true, true, List.of("USER"),
                        null, false, BanStatus.ACTIVE
                )
        );

        MeResponse response = meService.getCurrentUser(jwt("kc-id", "bob@example.com"));

        assertEquals("kc-id", response.getId());
        assertEquals(List.of("USER"), response.getRoles());
        verify(keycloakRealmRoleService, times(0)).ensureRealmRoleAssigned(eq("kc-id"), eq("USER"));
    }

    @Test
    void shouldAssignUserRoleWhenMissingAndRefreshProfile() {
        AdminUserView withoutUserRole = new AdminUserView(
                "kc-id", "memoa", "m@example.com", "Me", "Moa",
                false, true, List.of("default-roles-finance-portal"),
                null, false, BanStatus.ACTIVE
        );
        AdminUserView withUserRole = new AdminUserView(
                "kc-id", "memoa", "m@example.com", "Me", "Moa",
                false, true, List.of("USER", "default-roles-finance-portal"),
                null, false, BanStatus.ACTIVE
        );

        when(keycloakRealmRoleService.hasExactRealmRole("kc-id", "USER")).thenReturn(false);
        when(keycloakUserAdminPort.getUser("kc-id")).thenReturn(withoutUserRole, withUserRole);
        when(keycloakRealmRoleService.ensureRealmRoleAssigned("kc-id", "USER")).thenReturn(true);

        MeResponse response = meService.getCurrentUser(jwt("kc-id", "memoa"));

        verify(keycloakRealmRoleService).ensureRealmRoleAssigned("kc-id", "USER");
        verify(keycloakUserAdminPort, times(2)).getUser("kc-id");
        assertTrue(response.getRoles().contains("USER"));
    }

    @Test
    void shouldFallbackToJwtWhenKeycloakLookupFails() {
        when(keycloakUserAdminPort.getUser("kc-id")).thenThrow(new RuntimeException("down"));

        MeResponse response = meService.getCurrentUser(jwt("kc-id", "bob"));

        assertEquals("kc-id", response.getId());
        assertEquals("bob", response.getUsername());
        assertTrue(response.isEnabled());
    }

    private static Jwt jwt(String subject, String username) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", username,
                        "email", username + "@example.com",
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
    }
}
