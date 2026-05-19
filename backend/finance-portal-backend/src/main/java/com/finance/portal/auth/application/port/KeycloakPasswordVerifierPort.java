package com.finance.portal.auth.application.port;

public interface KeycloakPasswordVerifierPort {

    /**
     * Mevcut şifreyi Keycloak token endpoint (resource owner password) ile doğrular.
     *
     * @return true şifre doğru; false yanlış kimlik bilgisi veya grant reddedildi
     */
    boolean verifyCurrentPassword(String username, String currentPassword);
}
