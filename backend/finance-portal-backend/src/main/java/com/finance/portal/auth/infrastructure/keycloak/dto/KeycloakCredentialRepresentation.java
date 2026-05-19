package com.finance.portal.auth.infrastructure.keycloak.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeycloakCredentialRepresentation {

    private String type = "password";
    private String value;
    private Boolean temporary = false;
}
