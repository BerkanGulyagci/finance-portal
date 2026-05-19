package com.finance.portal.auth.presentation.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeResponse {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean emailVerified;
    private List<String> roles;
    private boolean enabled;
}
