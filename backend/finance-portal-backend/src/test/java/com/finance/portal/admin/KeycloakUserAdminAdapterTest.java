package com.finance.portal.admin;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakUserAdminAdapter;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakUserAdminAdapterTest {

    @Test
    void mapRoleNamesShouldReturnEmptyListForNullOrEmptyArray() {
        assertTrue(KeycloakUserAdminAdapter.mapRoleNames(null).isEmpty());
        assertTrue(KeycloakUserAdminAdapter.mapRoleNames(new KeycloakRoleRepresentation[0]).isEmpty());
    }

    @Test
    void mapRoleNamesShouldExtractAndSortRoleNames() {
        KeycloakRoleRepresentation userRole = new KeycloakRoleRepresentation();
        userRole.setName("USER");
        KeycloakRoleRepresentation defaultRole = new KeycloakRoleRepresentation();
        defaultRole.setName("default-roles-finance-portal");
        KeycloakRoleRepresentation blankRole = new KeycloakRoleRepresentation();
        blankRole.setName("  ");

        List<String> names = KeycloakUserAdminAdapter.mapRoleNames(
                new KeycloakRoleRepresentation[]{defaultRole, userRole, blankRole}
        );

        assertEquals(List.of("USER", "default-roles-finance-portal"), names);
    }
}
