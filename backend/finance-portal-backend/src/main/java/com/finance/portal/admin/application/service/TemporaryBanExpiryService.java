package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.UserBanStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemporaryBanExpiryService {

    private final UserBanStatePort userBanStatePort;
    private final UnbanUserService unbanUserService;
    private final AdminUserEnrichmentService adminUserEnrichmentService;
    private final com.finance.portal.admin.application.port.KeycloakUserAdminPort keycloakUserAdminPort;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireTemporaryBans() {
        Instant now = Instant.now();
        List<UserBanState> expired = userBanStatePort.findExpiredTemporaryBans(now);
        for (UserBanState state : expired) {
            log.info("Temporary ban expired for user {}", state.getKeycloakUserId());
            unbanUserService.unbanUser(state.getKeycloakUserId());
        }
    }

    public AdminUserView expireIfNeeded(AdminUserView user) {
        if (user.getBanStatus() != BanStatus.TEMPORARY_BANNED || user.getBanUntil() == null) {
            return user;
        }
        if (user.getBanUntil().isAfter(Instant.now())) {
            return user;
        }
        unbanUserService.unbanUser(user.getId());
        return adminUserEnrichmentService.enrichUser(keycloakUserAdminPort.getUser(user.getId()));
    }

    public List<AdminUserView> expireIfNeeded(List<AdminUserView> users) {
        return users.stream().map(this::expireIfNeeded).toList();
    }
}
