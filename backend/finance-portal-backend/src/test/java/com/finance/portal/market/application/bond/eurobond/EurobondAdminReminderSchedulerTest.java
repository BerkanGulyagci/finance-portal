package com.finance.portal.market.application.bond.eurobond;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EurobondAdminReminderSchedulerTest {

    @Mock KeycloakUserAdminPort keycloakUserAdminPort;
    @Mock NotificationService notificationService;

    private EurobondAdminReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EurobondAdminReminderScheduler(keycloakUserAdminPort, notificationService);
    }

    private static AdminUserView admin(String id, String email) {
        return new AdminUserView(
                id, "admin-" + id, email, "Admin", "User",
                true, true, List.of("ADMIN"), null, false, BanStatus.ACTIVE, null);
    }

    @Test
    void reminder_sendsNotificationToEachAdmin() {
        when(keycloakUserAdminPort.findUsersByRealmRole("ADMIN")).thenReturn(List.of(
                admin("a1", "a1@example.com"),
                admin("a2", "a2@example.com")
        ));

        scheduler.sendMonthlyReminder();

        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).createAndSend(
                userIdCaptor.capture(),
                typeCaptor.capture(),
                titleCaptor.capture(),
                any(),                 // body
                any(),                 // emailHtml
                emailCaptor.capture(),
                eq(null));             // relatedAlarmId

        assertThat(userIdCaptor.getAllValues()).containsExactly("a1", "a2");
        assertThat(typeCaptor.getAllValues()).containsOnly(NotificationType.ADMIN);
        assertThat(titleCaptor.getAllValues()).allMatch(t -> t.contains("HMB"));
        assertThat(emailCaptor.getAllValues()).containsExactly("a1@example.com", "a2@example.com");
    }

    @Test
    void reminder_noAdmins_doesNothing() {
        when(keycloakUserAdminPort.findUsersByRealmRole("ADMIN")).thenReturn(List.of());

        scheduler.sendMonthlyReminder();

        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reminder_keycloakThrows_doesNotPropagate() {
        when(keycloakUserAdminPort.findUsersByRealmRole("ADMIN"))
                .thenThrow(new RuntimeException("kc down"));

        // No exception bubbles out — scheduler must be self-healing.
        scheduler.sendMonthlyReminder();

        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reminder_oneAdminFails_othersStillNotified() {
        when(keycloakUserAdminPort.findUsersByRealmRole("ADMIN")).thenReturn(List.of(
                admin("ok1", "ok1@example.com"),
                admin("fail", "fail@example.com"),
                admin("ok2", "ok2@example.com")
        ));
        // İkinci admin için bildirim gönderimi patlasın.
        when(notificationService.createAndSend(
                eq("fail"), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("smtp down"));

        scheduler.sendMonthlyReminder();

        // Tüm 3 admin için çağrı atılmış olmalı (biri patlasa bile loop devam etmiş).
        verify(notificationService, times(3)).createAndSend(
                any(), eq(NotificationType.ADMIN), any(), any(), any(), any(), eq(null));
    }
}
