package com.finance.portal.auth;

import com.finance.portal.auth.application.port.KeycloakPasswordVerifierPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.KeycloakUserProfilePort;
import com.finance.portal.auth.application.service.MeProfileService;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.auth.presentation.dto.ChangePasswordRequest;
import com.finance.portal.auth.presentation.dto.MeActionResponse;
import com.finance.portal.auth.presentation.dto.UpdateEmailRequest;
import com.finance.portal.auth.presentation.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeProfileServiceTest {

    @Mock
    KeycloakUserProfilePort keycloakUserProfilePort;

    @Mock
    KeycloakPasswordVerifierPort keycloakPasswordVerifierPort;

    @Mock
    KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;

    @Mock
    CentralBusinessLogService centralBusinessLogService;

    @InjectMocks
    MeProfileService meProfileService;

    @Test
    void updateProfileShouldUpdateKeycloakUser() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Ali");
        request.setLastName("Veli");

        MeActionResponse response = meProfileService.updateProfile(jwt("kc-1", "alice"), request);

        verify(keycloakUserProfilePort).updateProfile("kc-1", "Ali", "Veli");
        assertFalse(response.isRequiresReLogin());
    }

    @Test
    void updateEmailShouldTriggerVerificationAndRequireReLogin() {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setEmail("new@example.com");

        MeActionResponse response = meProfileService.updateEmail(jwt("kc-1", "alice"), request);

        verify(keycloakUserProfilePort).updateEmail("kc-1", "new@example.com");
        verify(keycloakRegistrationFollowUpPort).requestEmailVerificationForUser("kc-1");
        assertTrue(response.isRequiresReLogin());
    }

    @Test
    void changePasswordShouldRejectMismatchedConfirmation() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("newpass12");
        request.setConfirmPassword("different");

        assertThrows(IllegalArgumentException.class,
                () -> meProfileService.changePassword(jwt("kc-1", "alice"), request));
    }

    @Test
    void changePasswordShouldRejectInvalidCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("newpass12");
        request.setConfirmPassword("newpass12");

        when(keycloakPasswordVerifierPort.verifyCurrentPassword("alice", "wrong")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> meProfileService.changePassword(jwt("kc-1", "alice"), request));
    }

    @Test
    void changePasswordShouldResetWhenCurrentPasswordValid() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("newpass12");
        request.setConfirmPassword("newpass12");

        when(keycloakPasswordVerifierPort.verifyCurrentPassword("alice", "old")).thenReturn(true);

        MeActionResponse response = meProfileService.changePassword(jwt("kc-1", "alice"), request);

        verify(keycloakUserProfilePort).resetPassword("kc-1", "newpass12");
        verify(keycloakUserProfilePort).logoutAllSessions("kc-1", BusinessLogSupport.TRIGGER_PASSWORD_CHANGE);
        assertTrue(response.isRequiresReLogin());
    }

    private static Jwt jwt(String subject, String username) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", subject, "preferred_username", username)
        );
    }
}
