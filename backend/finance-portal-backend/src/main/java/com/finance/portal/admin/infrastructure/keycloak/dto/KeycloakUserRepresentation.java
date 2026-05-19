package com.finance.portal.admin.infrastructure.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakUserRepresentation {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private List<String> requiredActions;
    private Map<String, List<String>> attributes;
}
