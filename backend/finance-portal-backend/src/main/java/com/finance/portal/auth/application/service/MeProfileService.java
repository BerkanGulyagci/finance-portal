package com.finance.portal.auth.application.service;

import com.finance.portal.auth.application.port.KeycloakPasswordVerifierPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.KeycloakUserProfilePort;
import com.finance.portal.common.application.logging.BusinessLogMetadataSanitizer;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.auth.presentation.dto.ChangePasswordRequest;
import com.finance.portal.auth.presentation.dto.MeActionResponse;
import com.finance.portal.auth.presentation.dto.UpdateEmailRequest;
import com.finance.portal.auth.presentation.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MeProfileService {

    private final KeycloakUserProfilePort keycloakUserProfilePort;
    private final KeycloakPasswordVerifierPort keycloakPasswordVerifierPort;
    private final KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    private final CentralBusinessLogService centralBusinessLogService;

    public MeActionResponse updateProfile(Jwt jwt, UpdateProfileRequest request) {
        String userId = requireUserId(jwt);
        keycloakUserProfilePort.updateProfile(userId, request.getFirstName().trim(), request.getLastName().trim());

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_PROFILE_UPDATED,
                "INFO",
                "Profile updated",
                "USER",
                userId,
                BusinessLogSupport.ACTION_UPDATE,
                BusinessLogSupport.RESULT_SUCCESS,
                Map.of("changedFields", List.of("firstName", "lastName")),
                userId,
                MeProfileService.class.getName()
        );

        return new MeActionResponse(false);
    }

    public MeActionResponse updateEmail(Jwt jwt, UpdateEmailRequest request) {
        String userId = requireUserId(jwt);
        String normalizedEmail = request.getEmail().trim();
        keycloakUserProfilePort.updateEmail(userId, normalizedEmail);
        keycloakRegistrationFollowUpPort.requestEmailVerificationForUser(userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("verificationRequested", true);
        String emailDomain = BusinessLogMetadataSanitizer.extractEmailDomain(normalizedEmail);
        if (emailDomain != null) {
            metadata.put("emailDomain", emailDomain);
        }
        String emailHash = BusinessLogMetadataSanitizer.hashEmail(normalizedEmail);
        if (emailHash != null) {
            metadata.put("emailHash", emailHash);
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_EMAIL_CHANGED,
                "INFO",
                "Email changed",
                "USER",
                userId,
                BusinessLogSupport.ACTION_UPDATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                MeProfileService.class.getName()
        );

        return new MeActionResponse(true);
    }

    public MeActionResponse changePassword(Jwt jwt, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Yeni şifre ve tekrarı eşleşmiyor.");
        }

        String userId = requireUserId(jwt);
        String username = readUsername(jwt);
        if (username == null) {
            throw new IllegalArgumentException("Kullanıcı adı token içinde bulunamadı.");
        }

        if (!keycloakPasswordVerifierPort.verifyCurrentPassword(username, request.getCurrentPassword())) {
            throw new IllegalArgumentException(
                    "Mevcut şifre doğrulanamadı. Şifrenizi kontrol edin; OTP aktifse Hesap Ayarları üzerinden değiştirin."
            );
        }

        keycloakUserProfilePort.resetPassword(userId, request.getNewPassword());
        keycloakUserProfilePort.logoutAllSessions(userId, BusinessLogSupport.TRIGGER_PASSWORD_CHANGE);

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_PASSWORD_CHANGED,
                "INFO",
                "Password changed",
                "USER",
                userId,
                BusinessLogSupport.ACTION_UPDATE,
                BusinessLogSupport.RESULT_SUCCESS,
                Map.of("sessionsRevoked", true),
                userId,
                MeProfileService.class.getName()
        );

        return new MeActionResponse(true);
    }

    private static String requireUserId(Jwt jwt) {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Geçersiz oturum.");
        }
        return userId;
    }

    private static String readUsername(Jwt jwt) {
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return jwt.getClaimAsString("username");
    }
}
