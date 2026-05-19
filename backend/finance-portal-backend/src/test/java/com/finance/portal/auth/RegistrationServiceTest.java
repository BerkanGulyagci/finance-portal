package com.finance.portal.auth;

import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.auth.application.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    UserRegistrationPort userRegistrationPort;

    @Mock
    KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;

    @InjectMocks
    RegistrationService registrationService;

    @Test
    void shouldRegisterInLdapThenRequestEmailVerification() {
        RegisterUserCommand command = new RegisterUserCommand(
                "newuser", "new@example.com", "secret", "Ada", "Lovelace"
        );

        registrationService.register(command);

        verify(userRegistrationPort).register(command);
        verify(keycloakRegistrationFollowUpPort).requestEmailVerificationIfUserExists("newuser");
        verifyNoMoreInteractions(userRegistrationPort, keycloakRegistrationFollowUpPort);
    }
}
