package com.finance.portal.admin.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class UserBanState {

    private final String keycloakUserId;
    private final boolean permanent;
    private final Instant banUntil;
    private final Instant createdAt;
    private final String banReason;
}
