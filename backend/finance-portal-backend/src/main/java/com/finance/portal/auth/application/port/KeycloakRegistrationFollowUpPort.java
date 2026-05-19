package com.finance.portal.auth.application.port;

/**
 * LDAP kaydı sonrası Keycloak tarafında email doğrulama adımlarını tetikler.
 */
public interface KeycloakRegistrationFollowUpPort {

    /**
     * Kullanıcı Keycloak'ta bulunursa VERIFY_EMAIL gönderir.
     * Bulunamazsa sessizce atlanır (ilk login'de required action devreye girer).
     */
    void requestEmailVerificationIfUserExists(String username);
}
