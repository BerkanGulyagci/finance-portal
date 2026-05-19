package com.finance.portal.auth.infrastructure.keycloak;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "keycloak.portal")
public class KeycloakPortalProperties {

    private String publicClientId = "finance-portal-api";

    /**
     * Email doğrulama linkinden sonra kullanıcının yönlendirileceği frontend URL.
     */
    private String postVerifyRedirectUri = "http://localhost:5173/login";
}
