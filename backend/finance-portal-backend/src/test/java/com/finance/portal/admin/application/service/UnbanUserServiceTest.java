package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.newsletter.application.service.NewsletterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UnbanUserServiceTest {

    private KeycloakUserAdminPort keycloak;
    private UserBanStatePort banStatePort;
    private UserAccountStatusPort accountStatus;
    private CentralBusinessLogService businessLog;
    private AlarmService alarmService;
    private NewsletterService newsletterService;
    private UnbanUserService service;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakUserAdminPort.class);
        banStatePort = mock(UserBanStatePort.class);
        accountStatus = mock(UserAccountStatusPort.class);
        businessLog = mock(CentralBusinessLogService.class);
        alarmService = mock(AlarmService.class);
        newsletterService = mock(NewsletterService.class);
        // defaults
        when(alarmService.reenableBanDisabledForUser(any())).thenReturn(0);
        when(newsletterService.reenableForUser(any())).thenReturn(false);
        service = new UnbanUserService(keycloak, banStatePort, accountStatus,
                businessLog, alarmService, newsletterService);
    }

    @Test
    @DisplayName("unbanUser: tam zincir — KC fetch + ban state sil + KC enable + cache evict + cascade reenable + audit")
    void unbanUser_fullChain() {
        AdminUserView u = view("u1");
        when(keycloak.getUser("u1")).thenReturn(u);
        when(alarmService.reenableBanDisabledForUser("u1")).thenReturn(3);
        when(newsletterService.reenableForUser("u1")).thenReturn(true);

        service.unbanUser("u1");

        verify(keycloak).getUser("u1");
        verify(banStatePort).deleteByUserId("u1");
        verify(keycloak).setUserEnabled("u1", true);
        verify(accountStatus).evictAccountStatus("u1");
        verify(alarmService).reenableBanDisabledForUser("u1");
        verify(newsletterService).reenableForUser("u1");
        // audit meta'da reenabledAlarms=3, newsletterReenabled=true
        ArgumentCaptor<Map<String, Object>> metaCap = (ArgumentCaptor<Map<String, Object>>)(ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(businessLog).publish(any(), any(), any(), any(), any(), eq("u1"), any(), any(),
                metaCap.capture(), any(), any());
        assertThat(metaCap.getValue())
                .containsEntry("targetUserId", "u1")
                .containsEntry("reenabledAlarms", 3)
                .containsEntry("newsletterReenabled", true);
        // actingAdminUserId verilmediği için audit'te yok
        assertThat(metaCap.getValue()).doesNotContainKey("adminUserId");
    }

    @Test
    @DisplayName("unbanUser(userId, actingAdminUserId): admin id audit'e dahil edilir")
    void unbanUser_withActingAdmin_includedInMeta() {
        AdminUserView u = view("u1");
        when(keycloak.getUser("u1")).thenReturn(u);

        service.unbanUser("u1", "admin-x");

        ArgumentCaptor<Map<String, Object>> metaCap = (ArgumentCaptor<Map<String, Object>>)(ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(businessLog).publish(any(), any(), any(), any(), any(), eq("u1"), any(), any(),
                metaCap.capture(), eq("admin-x"), any());
        assertThat(metaCap.getValue()).containsEntry("adminUserId", "admin-x");
    }

    @Test
    @DisplayName("unbanUser: cascade hiç enable etmediyse (0 alarm + false newsletter) yine audit atılır")
    void unbanUser_noCascadeChanges_stillAudits() {
        AdminUserView u = view("u1");
        when(keycloak.getUser("u1")).thenReturn(u);

        service.unbanUser("u1");

        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static AdminUserView view(String id) {
        return new AdminUserView(
                id, id, id + "@x.com", "F", "L",
                true, true, List.of(), null, false, BanStatus.ACTIVE, null);
    }
}
