package com.finance.portal.auth.application.service;

import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final UserRegistrationPort userRegistrationPort;

    public RegistrationService(UserRegistrationPort userRegistrationPort) {
        this.userRegistrationPort = userRegistrationPort;
    }

    public void register(RegisterUserCommand command) {
        userRegistrationPort.register(command);
    }
}
