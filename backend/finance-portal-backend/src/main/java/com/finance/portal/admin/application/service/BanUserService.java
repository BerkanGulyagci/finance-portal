package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.exception.AdminPolicyException;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BanUserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;
    private final CentralBusinessLogService centralBusinessLogService;

    public void banUser(String targetUserId, String actingAdminUserId, BanUserRequest request) {
        BanRequestValidator.validate(request);
        assertBanAllowed(targetUserId, actingAdminUserId);

        Instant now = Instant.now();
        if (request.getBanType() == BanType.PERMANENT) {
            userBanStatePort.save(new UserBanState(targetUserId, true, null, now));
        } else {
            Instant banUntil = BanDurationCalculator.calculateBanUntil(
                    request.getDurationValue(),
                    request.getDurationUnit()
            );
            userBanStatePort.save(new UserBanState(targetUserId, false, banUntil, now));
        }

        keycloakUserAdminPort.setUserEnabled(targetUserId, false);
        keycloakUserAdminPort.logoutUserSessions(targetUserId);
        userAccountStatusPort.evictAccountStatus(targetUserId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", targetUserId);
        metadata.put("adminUserId", actingAdminUserId);
        metadata.put("banType", request.getBanType().name());
        if (request.getBanType() != BanType.PERMANENT) {
            metadata.put("durationValue", request.getDurationValue());
            metadata.put("durationUnit", request.getDurationUnit().name());
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_BANNED,
                "WARN",
                "User banned",
                "USER",
                targetUserId,
                BusinessLogSupport.ACTION_BAN,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                actingAdminUserId,
                BanUserService.class.getName()
        );
    }

    private void assertBanAllowed(String targetUserId, String actingAdminUserId) {
        if (targetUserId.equals(actingAdminUserId)) {
            throw new IllegalArgumentException("Kendi hesabınızı banlayamazsınız.");
        }

        AdminUserView target = keycloakUserAdminPort.getUser(targetUserId);
        if (hasAdminRole(target.getRoles())) {
            throw new AdminPolicyException("ADMIN rolüne sahip kullanıcılar banlanamaz.", HttpStatus.FORBIDDEN);
        }
    }

    private static boolean hasAdminRole(List<String> roles) {
        return roles != null && roles.stream().anyMatch(ADMIN_ROLE::equals);
    }
}
