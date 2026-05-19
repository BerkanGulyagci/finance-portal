package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.exception.AdminPolicyException;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BanUserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;

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
