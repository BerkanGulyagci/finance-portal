package com.finance.portal.admin.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class AdminUserView {

    private final String id;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final boolean emailVerified;
    private final boolean enabled;
    private final List<String> roles;
    private final Instant banUntil;
    private final boolean permanentBan;
    private final BanStatus banStatus;
    private final String banReason;
}
