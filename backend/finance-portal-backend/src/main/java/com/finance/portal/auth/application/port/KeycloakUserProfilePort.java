package com.finance.portal.auth.application.port;

public interface KeycloakUserProfilePort {

    void updateProfile(String userId, String firstName, String lastName);

    void updateEmail(String userId, String newEmail);

    void resetPassword(String userId, String newPassword);

    void logoutAllSessions(String userId);
}
