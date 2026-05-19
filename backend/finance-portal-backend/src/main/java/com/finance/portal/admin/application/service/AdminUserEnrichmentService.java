package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.UserBanStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserEnrichmentService {

    private final UserBanStatePort userBanStatePort;

    public List<AdminUserView> enrichUsers(List<AdminUserView> keycloakUsers) {
        if (keycloakUsers.isEmpty()) {
            return keycloakUsers;
        }
        List<String> userIds = keycloakUsers.stream().map(AdminUserView::getId).toList();
        Map<String, UserBanState> banStates = userBanStatePort.findByUserIds(userIds);
        return keycloakUsers.stream()
                .map(user -> enrich(user, banStates.get(user.getId())))
                .collect(Collectors.toList());
    }

    public AdminUserView enrichUser(AdminUserView keycloakUser) {
        return enrich(keycloakUser, userBanStatePort.findByUserId(keycloakUser.getId()).orElse(null));
    }

    private AdminUserView enrich(AdminUserView keycloakUser, UserBanState banState) {
        BanStatus banStatus = BanStatusResolver.resolve(keycloakUser.isEnabled(), banState);
        boolean permanentBan = banState != null && banState.isPermanent();
        java.time.Instant banUntil = banState != null ? banState.getBanUntil() : null;

        return new AdminUserView(
                keycloakUser.getId(),
                keycloakUser.getUsername(),
                keycloakUser.getEmail(),
                keycloakUser.getFirstName(),
                keycloakUser.getLastName(),
                keycloakUser.isEmailVerified(),
                keycloakUser.isEnabled(),
                keycloakUser.getRoles(),
                banUntil,
                permanentBan,
                banStatus
        );
    }
}
