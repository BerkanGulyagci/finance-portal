package com.finance.portal.auth.application.port;

/**
 * LDAP kaydı sonrası Keycloak tarafında USER rolü ve email doğrulama adımlarını tetikler.
 */
public interface KeycloakRegistrationFollowUpPort {

    /**
     * Kullanıcı Keycloak'ta bulunursa USER realm rolünü atar.
     * Bulunamazsa sessizce atlanır (rol ilk /api/v1/me çağrısında tekrar denenir).
     *
     * NOT: E-posta doğrulaması burada GÖNDERİLMEZ. Realm `verifyEmail:true` ayarı
     * sayesinde doğrulama, kullanıcı ilk kez login olduğunda standart Keycloak
     * VERIFY_EMAIL akışıyla (doğru host'ta link üreten "Verify email" e-postası)
     * tetiklenir. Kayıt sırasında ayrıca execute-actions-email çağrılmaz; aksi hâlde
     * ikinci ("Update Your Account") + yanlış host'lu kırık bir doğrulama maili oluşurdu.
     */
    void requestEmailVerificationIfUserExists(String username);

    /**
     * Bilinen Keycloak kullanıcı kimliği için VERIFY_EMAIL e-postası gönderir.
     */
    void requestEmailVerificationForUser(String userId);
}
