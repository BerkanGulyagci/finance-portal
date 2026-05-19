package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnbanUserService {

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;

    public void unbanUser(String userId) {
        keycloakUserAdminPort.getUser(userId);
        userBanStatePort.deleteByUserId(userId);
        keycloakUserAdminPort.setUserEnabled(userId, true);
        userAccountStatusPort.evictAccountStatus(userId);
    }
}
