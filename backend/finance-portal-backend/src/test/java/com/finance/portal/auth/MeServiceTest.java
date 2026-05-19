package com.finance.portal.auth;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    KeycloakUserAdminPort keycloakUserAdminPort;

    @InjectMocks
    MeService meService;

    @Test
    void shouldReturnKeycloakProfileWhenAvailable() {
        when(keycloakUserAdminPort.getUser("kc-id")).thenReturn(
                new AdminUserView(
                        "kc-id", "alice", "alice@example.com", "Ali", "Veli",
                        true, true, List.of("USER"),
                        null, false, BanStatus.ACTIVE
                )
        );

        MeResponse response = meService.getCurrentUser(jwt("kc-id", "bob@example.com"));

        assertEquals("kc-id", response.getId());
        assertEquals("alice", response.getUsername());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("Ali", response.getFirstName());
        assertEquals("Veli", response.getLastName());
        assertTrue(response.isEmailVerified());
        assertTrue(response.isEnabled());
        assertEquals(List.of("USER"), response.getRoles());
    }

    @Test
    void shouldFallbackToJwtWhenKeycloakLookupFails() {
        when(keycloakUserAdminPort.getUser("kc-id")).thenThrow(new RuntimeException("down"));

        MeResponse response = meService.getCurrentUser(jwt("kc-id", "bob@example.com"));

        assertEquals("kc-id", response.getId());
        assertEquals("bob", response.getUsername());
        assertEquals("bob@example.com", response.getEmail());
        assertTrue(response.isEnabled());
    }

    private static Jwt jwt(String subject, String email) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", "bob",
                        "email", email,
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
    }
}
