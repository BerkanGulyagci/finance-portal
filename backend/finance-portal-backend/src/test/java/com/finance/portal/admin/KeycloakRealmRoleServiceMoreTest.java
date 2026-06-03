package com.finance.portal.admin;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleAdminClient;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleService;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage focused companion to {@link KeycloakRealmRoleServiceTest}.
 * Targets the null/blank guards, the resolve-role arms, every catch path in
 * assignRealmRole (409 conflict / 403 forbidden / generic), the cause-chain
 * traversal in hasStatus, ResourceNotFoundException handling and the
 * configured-role name fallback — branches the original test does not exercise.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakRealmRoleServiceMoreTest {

    @Mock
    KeycloakRealmRoleAdminClient realmRoleAdminClient;

    @Mock
    CentralBusinessLogService centralBusinessLogService;

    KeycloakAdminProperties adminProperties;
    KeycloakRealmRoleService service;

    @BeforeEach
    void setUp() {
        adminProperties = new KeycloakAdminProperties();
        adminProperties.setUrl("http://localhost:8081");
        adminProperties.setRealm("finance-portal");
        adminProperties.setDefaultUserRoleId("98436eeb-2961-41e5-a23d-c150db93d649");
        adminProperties.setDefaultUserRoleName("USER");
        service = new KeycloakRealmRoleService(realmRoleAdminClient, adminProperties, centralBusinessLogService);
    }

    // ---- null / blank input guards (line 33) ----

    @Test
    void returnsFalseWhenUserIdIsNull() {
        assertFalse(service.ensureRealmRoleAssigned(null, "USER"));
        verify(realmRoleAdminClient, never()).getUserRealmRoleMappings(any());
    }

    @Test
    void returnsFalseWhenUserIdIsBlank() {
        assertFalse(service.ensureRealmRoleAssigned("   ", "USER"));
        verify(realmRoleAdminClient, never()).getUserRealmRoleMappings(any());
    }

    @Test
    void returnsFalseWhenRoleNameIsNull() {
        assertFalse(service.ensureRealmRoleAssigned("u1", null));
        verify(realmRoleAdminClient, never()).getUserRealmRoleMappings(any());
    }

    @Test
    void returnsFalseWhenRoleNameIsBlank() {
        assertFalse(service.ensureRealmRoleAssigned("u1", ""));
        verify(realmRoleAdminClient, never()).getUserRealmRoleMappings(any());
    }

    // ---- resolveRealmRole: API returns role with usable id (non-USER role, no fallback) ----

    @Test
    void assignsWhenApiReturnsRoleWithValidIdForNonUserRole() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation adminRole = new KeycloakRoleRepresentation();
        adminRole.setId("admin-role-id");
        adminRole.setName("ADMIN");
        when(realmRoleAdminClient.getRealmRoleByName("ADMIN")).thenReturn(adminRole);

        assertTrue(service.ensureRealmRoleAssigned("u1", "ADMIN"));

        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());
    }

    // ---- resolveRealmRole: API role has blank id, non-USER role -> no fallback -> null -> false ----

    @Test
    void returnsFalseWhenApiRoleHasBlankIdAndRoleIsNotUser() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation blankIdRole = new KeycloakRoleRepresentation();
        blankIdRole.setId("   ");
        blankIdRole.setName("ADMIN");
        when(realmRoleAdminClient.getRealmRoleByName("ADMIN")).thenReturn(blankIdRole);

        assertFalse(service.ensureRealmRoleAssigned("u1", "ADMIN"));

        verify(realmRoleAdminClient, never()).assignRealmRolesToUser(eq("u1"), any());
    }

    // ---- resolveRealmRole: API role has null id but role is USER -> configured fallback used ----

    @Test
    void fallsBackToConfiguredWhenApiRoleHasNullId() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation nullIdRole = new KeycloakRoleRepresentation();
        nullIdRole.setId(null);
        nullIdRole.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(nullIdRole);

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), captor.capture());
        // configured id is used because API role id was unusable
        org.junit.jupiter.api.Assertions.assertEquals(
                "98436eeb-2961-41e5-a23d-c150db93d649", captor.getValue().get(0).getId());
    }

    // ---- tryFetchRealmRoleByName: ResourceNotFoundException catch (line 105) ----

    @Test
    void resourceNotFoundFromApiFallsBackToConfiguredForUserRole() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("USER"))
                .thenThrow(new ResourceNotFoundException("Realm role 'USER' not found"));

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());
    }

    @Test
    void resourceNotFoundForNonUserRoleReturnsFalse() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("ADMIN"))
                .thenThrow(new ResourceNotFoundException("Realm role 'ADMIN' not found"));

        assertFalse(service.ensureRealmRoleAssigned("u1", "ADMIN"));

        verify(realmRoleAdminClient, never()).assignRealmRolesToUser(eq("u1"), any());
    }

    // ---- configuredUserRole: blank configured role name -> falls back to DEFAULT_USER_REALM_ROLE ----

    @Test
    void configuredRoleUsesDefaultNameWhenConfiguredNameBlank() {
        adminProperties.setDefaultUserRoleName("   ");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("USER"))
                .thenThrow(new ExternalApiException("status=403"));

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("USER", captor.getValue().get(0).getName());
    }

    @Test
    void configuredRoleUsesDefaultNameWhenConfiguredNameNull() {
        adminProperties.setDefaultUserRoleName(null);
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("USER"))
                .thenThrow(new ExternalApiException("status=403"));

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("USER", captor.getValue().get(0).getName());
    }

    // ---- assignRealmRole catch: 409 conflict -> treated as success ----

    @Test
    void conflictOnAssignIsTreatedAsSuccess() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    // ---- assignRealmRole catch: 403 forbidden -> false (forbidden hint branch) ----

    @Test
    void forbiddenOnAssignReturnsFalse() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertFalse(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    // ---- assignRealmRole catch: generic 500 (not conflict, not forbidden) -> false ----

    @Test
    void serverErrorOnAssignReturnsFalse() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        doThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Boom", null, null, null))
                .when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertFalse(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    // ---- assignRealmRole catch: non-HTTP exception (null message) -> false; exercises hasStatus
    //      cause-chain walk to null and extractErrorDetail's class-name fallback ----

    @Test
    void nonHttpExceptionWithNullMessageOnAssignReturnsFalse() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        doThrow(new IllegalStateException((String) null))
                .when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertFalse(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    // ---- hasStatus / extractErrorDetail: RestClientResponseException wrapped as a CAUSE ----

    @Test
    void conflictWrappedAsCauseIsTreatedAsSuccess() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        RuntimeException wrapped = new RuntimeException(
                "wrapped",
                HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null));
        doThrow(wrapped).when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    @Test
    void forbiddenWrappedAsCauseReturnsFalse() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        RuntimeException wrapped = new RuntimeException(
                "wrapped",
                HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));
        doThrow(wrapped).when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertFalse(service.ensureRealmRoleAssigned("u1", "USER"));
    }

    // ---- hasExactRealmRole: empty mappings array -> anyMatch false -> proceeds to resolve & assign ----

    @Test
    void emptyExistingRoleMappingsLeadsToAssignment() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation();
        role.setId("rid");
        role.setName("USER");
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(role);
        doNothing().when(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), any());
    }

    // ---- hasExactRealmRole direct: role present but name mismatch -> false ----

    @Test
    void hasExactRealmRoleFalseWhenNameDiffers() {
        KeycloakRoleRepresentation other = new KeycloakRoleRepresentation();
        other.setName("ADMIN");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[]{other});

        assertFalse(service.hasExactRealmRole("u1", "USER"));
    }

    @Test
    void hasExactRealmRoleTrueWhenNameMatches() {
        KeycloakRoleRepresentation match = new KeycloakRoleRepresentation();
        match.setName("USER");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[]{match});

        assertTrue(service.hasExactRealmRole("u1", "USER"));
    }
}
