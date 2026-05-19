package com.finance.portal.admin.infrastructure.keycloak;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    private String url = "http://finance-portal-keycloak:8080";
    private String realm = "finance-portal";
    private String clientId = "finance-portal-admin-service";
    private String clientSecret = "";

    /**
     * GET /roles/USER 403 verdiğinde (view-realm yok) POST için kullanılır.
     * Keycloak Admin → Realm roles → USER → Details içindeki ID.
     */
    private String defaultUserRoleName = "USER";
    private String defaultUserRoleId = "";

    public String tokenEndpoint() {
        return trimTrailingSlash(url) + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String adminApiBase() {
        return trimTrailingSlash(url) + "/admin/realms/" + realm;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
