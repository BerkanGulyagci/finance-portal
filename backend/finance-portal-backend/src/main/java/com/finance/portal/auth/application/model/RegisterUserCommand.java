package com.finance.portal.auth.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Kullanıcı kaydı use-case girdisi (presentation'dan bağımsız).
 */
@Getter
@AllArgsConstructor
public class RegisterUserCommand {

    private final String username;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
}
