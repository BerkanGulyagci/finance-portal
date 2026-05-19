package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakRoleRepresentation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KeycloakRealmRoleAdminClient {

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminRestClient restClient;

    public KeycloakRoleRepresentation getRealmRoleByName(String roleName) {
        return restClient.get(realmRoleUrl(roleName), KeycloakRoleRepresentation.class);
    }

    public KeycloakRoleRepresentation[] getUserRealmRoleMappings(String userId) {
        KeycloakRoleRepresentation[] roles = restClient.get(
                userRoleMappingsUrl(userId),
                KeycloakRoleRepresentation[].class
        );
        return roles != null ? roles : new KeycloakRoleRepresentation[0];
    }

    public void assignRealmRolesToUser(String userId, List<KeycloakRoleRepresentation> roles) {
        restClient.post(userRoleMappingsUrl(userId), roles);
    }

    private String userRoleMappingsUrl(String userId) {
        return properties.adminApiBase() + "/users/" + userId + "/role-mappings/realm";
    }

    private String realmRoleUrl(String roleName) {
        return properties.adminApiBase() + "/roles/" + roleName;
    }
}
