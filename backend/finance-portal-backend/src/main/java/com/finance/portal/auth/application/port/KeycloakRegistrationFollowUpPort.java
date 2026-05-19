package com.finance.portal.auth.application.port;

/**
 * LDAP kaydı sonrası Keycloak tarafında USER rolü ve email doğrulama adımlarını tetikler.
 */
public interface KeycloakRegistrationFollowUpPort {

    /**
     * Kullanıcı Keycloak'ta bulunursa USER realm rolünü atar ve VERIFY_EMAIL gönderir.
     * Bulunamazsa sessizce atlanır (ilk login'de required action devreye girer).
     */
    void requestEmailVerificationIfUserExists(String username);

    /**
     * Bilinen Keycloak kullanıcı kimliği için VERIFY_EMAIL e-postası gönderir.
     */
    void requestEmailVerificationForUser(String userId);
}
