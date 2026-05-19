package com.finance.portal.admin;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleAdminClient;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakRealmRoleService;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import com.finance.portal.common.application.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakRealmRoleServiceTest {

    @Mock
    KeycloakRealmRoleAdminClient realmRoleAdminClient;

    KeycloakAdminProperties adminProperties;
    KeycloakRealmRoleService service;

    @BeforeEach
    void setUp() {
        adminProperties = new KeycloakAdminProperties();
        adminProperties.setUrl("http://localhost:8081");
        adminProperties.setRealm("finance-portal");
        adminProperties.setDefaultUserRoleId("98436eeb-2961-41e5-a23d-c150db93d649");
        adminProperties.setDefaultUserRoleName("USER");
        service = new KeycloakRealmRoleService(realmRoleAdminClient, adminProperties);
    }

    @Test
    void ensureRealmRoleAssignedShouldSkipWhenUserAlreadyHasUserRole() {
        KeycloakRoleRepresentation userRole = new KeycloakRoleRepresentation();
        userRole.setName("USER");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[]{userRole});

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        verify(realmRoleAdminClient, never()).assignRealmRolesToUser(eq("u1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void defaultRolesShouldNotCountAsUserRole() {
        KeycloakRoleRepresentation defaultRole = new KeycloakRoleRepresentation();
        defaultRole.setName("default-roles-finance-portal");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[]{defaultRole});

        KeycloakRoleRepresentation realmRole = fullUserRole();
        when(realmRoleAdminClient.getRealmRoleByName("USER")).thenReturn(realmRole);

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), captor.capture());
        assertEquals("USER", captor.getValue().get(0).getName());
    }

    @Test
    void shouldUseConfiguredRoleIdWhenGetRoleByNameReturns403() {
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("USER"))
                .thenThrow(new ExternalApiException(
                        "Keycloak Admin API isteği başarısız oldu: status=403 body={\"error\":\"HTTP 403 Forbidden\"}"
                ));

        assertTrue(service.ensureRealmRoleAssigned("u1", "USER"));

        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(realmRoleAdminClient).assignRealmRolesToUser(eq("u1"), captor.capture());
        assertEquals("98436eeb-2961-41e5-a23d-c150db93d649", captor.getValue().get(0).getId());
        assertEquals("USER", captor.getValue().get(0).getName());
    }

    @Test
    void ensureRealmRoleAssignedShouldReturnFalseWhenRoleUnresolved() {
        adminProperties.setDefaultUserRoleId("");
        when(realmRoleAdminClient.getUserRealmRoleMappings("u1"))
                .thenReturn(new KeycloakRoleRepresentation[0]);
        when(realmRoleAdminClient.getRealmRoleByName("USER"))
                .thenThrow(new ExternalApiException("status=403"));

        assertFalse(service.ensureRealmRoleAssigned("u1", "USER"));
        verify(realmRoleAdminClient, never()).assignRealmRolesToUser(eq("u1"), org.mockito.ArgumentMatchers.any());
    }

    private static KeycloakRoleRepresentation fullUserRole() {
        KeycloakRoleRepresentation realmRole = new KeycloakRoleRepresentation();
        realmRole.setId("role-user-id");
        realmRole.setName("USER");
        realmRole.setDescription("Standard user");
        realmRole.setComposite(false);
        realmRole.setClientRole(false);
        realmRole.setContainerId("finance-portal");
        return realmRole;
    }
}
