package com.finance.portal.auth.application.service;

import com.finance.portal.common.application.logging.BusinessLogMetadataSanitizer;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRegistrationPort userRegistrationPort;
    private final KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    private final CentralBusinessLogService centralBusinessLogService;

    public void register(RegisterUserCommand command) {
        userRegistrationPort.register(command);
        keycloakRegistrationFollowUpPort.requestEmailVerificationIfUserExists(command.getUsername());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", command.getUsername());
        metadata.put("outcome", BusinessLogSupport.RESULT_SUCCESS);
        String emailDomain = BusinessLogMetadataSanitizer.extractEmailDomain(command.getEmail());
        if (emailDomain != null) {
            metadata.put("emailDomain", emailDomain);
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_REGISTERED,
                "INFO",
                "User registered successfully",
                "USER",
                null,
                BusinessLogSupport.ACTION_REGISTER,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                null,
                RegistrationService.class.getName()
        );
    }
}
