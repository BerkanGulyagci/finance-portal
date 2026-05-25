package com.finance.portal.admin.application.port;

import com.finance.portal.admin.application.model.AdminUserView;

import java.util.List;

public interface KeycloakUserAdminPort {

    List<AdminUserView> listUsers(String search, int first, int max);

    /** Belirli bir realm rolüne sahip kullanıcılar (ör. "ADMIN") — bildirim göndermek için. */
    List<AdminUserView> findUsersByRealmRole(String roleName);

    AdminUserView getUser(String userId);

    void setUserEnabled(String userId, boolean enabled);

    void logoutUserSessions(String userId);
}
