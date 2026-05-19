package com.finance.portal.admin.presentation.dto;

import com.finance.portal.admin.application.model.BanStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class AdminUserResponse {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<String> roles;
    private Instant banUntil;
    private boolean permanentBan;
    private BanStatus banStatus;
}
