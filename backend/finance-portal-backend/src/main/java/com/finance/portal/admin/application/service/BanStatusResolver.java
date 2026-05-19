package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;

public final class BanStatusResolver {

    private BanStatusResolver() {
    }

    public static BanStatus resolve(boolean keycloakEnabled, UserBanState banState) {
        if (keycloakEnabled && banState == null) {
            return BanStatus.ACTIVE;
        }
        if (banState == null) {
            return BanStatus.PERMANENT_BANNED;
        }
        if (banState.isPermanent()) {
            return BanStatus.PERMANENT_BANNED;
        }
        if (banState.getBanUntil() != null) {
            return BanStatus.TEMPORARY_BANNED;
        }
        return BanStatus.PERMANENT_BANNED;
    }

    public static boolean isExpiredTemporaryBan(UserBanState banState, java.time.Instant now) {
        return banState != null
                && !banState.isPermanent()
                && banState.getBanUntil() != null
                && !banState.getBanUntil().isAfter(now);
    }
}
