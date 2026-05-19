package com.finance.portal.auth.infrastructure.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeycloakTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;
}
