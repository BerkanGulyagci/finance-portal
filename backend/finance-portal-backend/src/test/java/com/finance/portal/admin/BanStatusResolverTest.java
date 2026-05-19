package com.finance.portal.admin;

import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.service.BanStatusResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BanStatusResolverTest {

    @Test
    void shouldResolveActiveWhenEnabledWithoutBanState() {
        assertEquals(BanStatus.ACTIVE, BanStatusResolver.resolve(true, null));
    }

    @Test
    void shouldResolvePermanentWhenDisabledWithoutBanState() {
        assertEquals(BanStatus.PERMANENT_BANNED, BanStatusResolver.resolve(false, null));
    }

    @Test
    void shouldResolveTemporaryBan() {
        UserBanState state = new UserBanState(
                "id",
                false,
                Instant.now().plusSeconds(3600),
                Instant.now()
        );
        assertEquals(BanStatus.TEMPORARY_BANNED, BanStatusResolver.resolve(false, state));
    }

    @Test
    void shouldResolvePermanentBanFromState() {
        UserBanState state = new UserBanState("id", true, null, Instant.now());
        assertEquals(BanStatus.PERMANENT_BANNED, BanStatusResolver.resolve(false, state));
    }
}
