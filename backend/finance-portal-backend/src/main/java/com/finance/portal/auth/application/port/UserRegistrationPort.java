package com.finance.portal.auth.application.port;

import com.finance.portal.auth.application.model.RegisterUserCommand;

/**
 * Kullanıcı kaydı adaptör portu (LDAP vb.).
 */
public interface UserRegistrationPort {

    void register(RegisterUserCommand command);
}
