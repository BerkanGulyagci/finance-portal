package com.finance.portal.auth.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminRestClient;
import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakUserRepresentation;
import com.finance.portal.auth.application.port.KeycloakUserProfilePort;
import com.finance.portal.auth.infrastructure.keycloak.dto.KeycloakCredentialRepresentation;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KeycloakUserProfileAdapter implements KeycloakUserProfilePort {

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminRestClient restClient;
    private final CentralBusinessLogService centralBusinessLogService;

    @Override
    public void updateProfile(String userId, String firstName, String lastName) {
        KeycloakUserRepresentation user = getUser(userId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        restClient.put(userUrl(userId), user);
    }

    @Override
    public void updateEmail(String userId, String newEmail) {
        KeycloakUserRepresentation user = getUser(userId);
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Yeni email adresi mevcut adres ile aynı.");
        }
        user.setEmail(newEmail);
        user.setEmailVerified(false);
        List<String> requiredActions = user.getRequiredActions() != null
                ? new ArrayList<>(user.getRequiredActions())
                : new ArrayList<>();
        if (!requiredActions.contains("VERIFY_EMAIL")) {
            requiredActions.add("VERIFY_EMAIL");
        }
        user.setRequiredActions(requiredActions);
        restClient.put(userUrl(userId), user);
    }

    @Override
    public void resetPassword(String userId, String newPassword) {
        KeycloakCredentialRepresentation credential = new KeycloakCredentialRepresentation();
        credential.setValue(newPassword);
        restClient.put(userUrl(userId) + "/reset-password", credential);
    }

    @Override
    public void logoutAllSessions(String userId) {
        logoutAllSessions(userId, null);
    }

    @Override
    public void logoutAllSessions(String userId, String auditTrigger) {
        try {
            restClient.post(properties.adminApiBase() + "/users/" + userId + "/logout");
            if (auditTrigger != null && !auditTrigger.isBlank()) {
                centralBusinessLogService.publish(
                        BusinessLogSupport.CATEGORY_AUDIT,
                        BusinessLogSupport.EVENT_USER_SESSIONS_REVOKED,
                        "INFO",
                        "User sessions revoked",
                        "USER",
                        userId,
                        BusinessLogSupport.ACTION_REVOKE,
                        BusinessLogSupport.RESULT_SUCCESS,
                        Map.of("userId", userId, "trigger", auditTrigger),
                        userId,
                        KeycloakUserProfileAdapter.class.getName()
                );
            }
        } catch (Exception ignored) {
            // Oturum sonlandırma başarısız olsa da şifre değişimi tamamlanmış olabilir
        }
    }

    private KeycloakUserRepresentation getUser(String userId) {
        return restClient.get(userUrl(userId), KeycloakUserRepresentation.class);
    }

    private String userUrl(String userId) {
        return properties.adminApiBase() + "/users/" + userId;
    }
}
