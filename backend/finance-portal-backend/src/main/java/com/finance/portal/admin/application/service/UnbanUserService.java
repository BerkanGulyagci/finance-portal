package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnbanUserService {

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;
    private final CentralBusinessLogService centralBusinessLogService;

    public void unbanUser(String userId) {
        unbanUser(userId, null);
    }

    public void unbanUser(String userId, String actingAdminUserId) {
        keycloakUserAdminPort.getUser(userId);
        userBanStatePort.deleteByUserId(userId);
        keycloakUserAdminPort.setUserEnabled(userId, true);
        userAccountStatusPort.evictAccountStatus(userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", userId);
        if (actingAdminUserId != null) {
            metadata.put("adminUserId", actingAdminUserId);
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_UNBANNED,
                "INFO",
                "User unbanned",
                "USER",
                userId,
                BusinessLogSupport.ACTION_UNBAN,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                actingAdminUserId,
                UnbanUserService.class.getName()
        );
    }
}
