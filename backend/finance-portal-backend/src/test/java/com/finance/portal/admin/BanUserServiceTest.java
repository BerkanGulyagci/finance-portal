package com.finance.portal.admin;

import com.finance.portal.admin.application.exception.AdminPolicyException;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.admin.application.service.BanUserService;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.newsletter.application.service.NewsletterService;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.admin.presentation.dto.DurationUnit;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BanUserServiceTest {

    @Mock
    KeycloakUserAdminPort keycloakUserAdminPort;

    @Mock
    UserBanStatePort userBanStatePort;

    @Mock
    UserAccountStatusPort userAccountStatusPort;

    @Mock
    CentralBusinessLogService centralBusinessLogService;

    @Mock
    AlarmService alarmService;

    @Mock
    NewsletterService newsletterService;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    BanUserService banUserService;

    @Test
    void shouldRejectSelfBan() {
        BanUserRequest request = permanentRequest();
        assertThrows(IllegalArgumentException.class,
                () -> banUserService.banUser("admin-id", "admin-id", request));
        verify(userBanStatePort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectBanningAdminUser() {
        when(keycloakUserAdminPort.getUser("target-id")).thenReturn(
                user("target-id", true, List.of("ADMIN"))
        );

        assertThrows(AdminPolicyException.class,
                () -> banUserService.banUser("target-id", "acting-admin-id", permanentRequest()));
    }

    @Test
    void shouldApplyPermanentBan() {
        when(keycloakUserAdminPort.getUser("target-id")).thenReturn(
                user("target-id", true, List.of("USER"))
        );

        assertDoesNotThrow(() -> banUserService.banUser("target-id", "acting-admin-id", permanentRequest()));

        ArgumentCaptor<UserBanState> stateCaptor = ArgumentCaptor.forClass(UserBanState.class);
        verify(userBanStatePort).save(stateCaptor.capture());
        assertTrue(stateCaptor.getValue().isPermanent());
        assertNull(stateCaptor.getValue().getBanUntil());
        verify(keycloakUserAdminPort).setUserEnabled("target-id", false);
        verify(keycloakUserAdminPort).logoutUserSessions("target-id");
    }

    @Test
    void shouldApplyTemporaryBan() {
        when(keycloakUserAdminPort.getUser("target-id")).thenReturn(
                user("target-id", true, List.of("USER"))
        );

        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.TEMPORARY);
        request.setDurationValue(8);
        request.setDurationUnit(DurationUnit.HOURS);

        banUserService.banUser("target-id", "acting-admin-id", request);

        ArgumentCaptor<UserBanState> stateCaptor = ArgumentCaptor.forClass(UserBanState.class);
        verify(userBanStatePort).save(stateCaptor.capture());
        assertFalse(stateCaptor.getValue().isPermanent());
        assertNotNull(stateCaptor.getValue().getBanUntil());
    }

    private static BanUserRequest permanentRequest() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.PERMANENT);
        return request;
    }

    private static AdminUserView user(String id, boolean enabled, List<String> roles) {
        return new AdminUserView(
                id, "user", "a@b.com", "A", "B", true, enabled, roles,
                null, false, enabled ? BanStatus.ACTIVE : BanStatus.PERMANENT_BANNED, null
        );
    }
}
