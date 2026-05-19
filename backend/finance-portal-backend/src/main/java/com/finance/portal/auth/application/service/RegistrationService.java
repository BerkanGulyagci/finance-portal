package com.finance.portal.auth.application.service;

import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRegistrationPort userRegistrationPort;
    private final KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;

    public void register(RegisterUserCommand command) {
        userRegistrationPort.register(command);
        keycloakRegistrationFollowUpPort.requestEmailVerificationIfUserExists(command.getUsername());
    }
}
