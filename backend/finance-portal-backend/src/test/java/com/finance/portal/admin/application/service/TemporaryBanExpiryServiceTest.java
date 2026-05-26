package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemporaryBanExpiryServiceTest {

    private UserBanStatePort banStatePort;
    private UnbanUserService unbanService;
    private AdminUserEnrichmentService enrichment;
    private KeycloakUserAdminPort keycloak;
    private CentralBusinessLogService businessLog;
    private TemporaryBanExpiryService service;

    @BeforeEach
    void setUp() {
        banStatePort = mock(UserBanStatePort.class);
        unbanService = mock(UnbanUserService.class);
        enrichment = mock(AdminUserEnrichmentService.class);
        keycloak = mock(KeycloakUserAdminPort.class);
        businessLog = mock(CentralBusinessLogService.class);
        service = new TemporaryBanExpiryService(banStatePort, unbanService, enrichment,
                keycloak, businessLog);
    }

    // ------------------------------ expireTemporaryBans (scheduled) ------------------------------

    @Test
    @DisplayName("expireTemporaryBans: süresi dolan ban yok → unban çağrılmaz")
    void expireTemporaryBans_noExpired_noUnban() {
        when(banStatePort.findExpiredTemporaryBans(any())).thenReturn(List.of());

        service.expireTemporaryBans();

        verifyNoInteractions(unbanService, businessLog);
    }

    @Test
    @DisplayName("expireTemporaryBans: süresi dolan ban için unban + audit log")
    void expireTemporaryBans_expired_unbansAndAudits() {
        UserBanState s1 = mock(UserBanState.class);
        when(s1.getKeycloakUserId()).thenReturn("u1");
        UserBanState s2 = mock(UserBanState.class);
        when(s2.getKeycloakUserId()).thenReturn("u2");
        when(banStatePort.findExpiredTemporaryBans(any())).thenReturn(List.of(s1, s2));

        service.expireTemporaryBans();

        verify(unbanService).unbanUser("u1");
        verify(unbanService).unbanUser("u2");
        verify(businessLog, times(2)).publish(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    // ------------------------------ expireIfNeeded (single user) ------------------------------

    @Test
    @DisplayName("expireIfNeeded(user): status ACTIVE → aynen döner")
    void expireIfNeeded_active_returnsSame() {
        AdminUserView u = view("u1", BanStatus.ACTIVE, null);

        AdminUserView out = service.expireIfNeeded(u);

        assertThat(out).isSameAs(u);
        verifyNoInteractions(unbanService);
    }

    @Test
    @DisplayName("expireIfNeeded(user): status PERMANENT_BANNED → aynen döner (geçici değil)")
    void expireIfNeeded_permanent_returnsSame() {
        AdminUserView u = view("u1", BanStatus.PERMANENT_BANNED, null);

        AdminUserView out = service.expireIfNeeded(u);

        assertThat(out).isSameAs(u);
        verifyNoInteractions(unbanService);
    }

    @Test
    @DisplayName("expireIfNeeded(user): banUntil null → aynen döner")
    void expireIfNeeded_nullUntil_returnsSame() {
        AdminUserView u = view("u1", BanStatus.TEMPORARY_BANNED, null);

        assertThat(service.expireIfNeeded(u)).isSameAs(u);
        verifyNoInteractions(unbanService);
    }

    @Test
    @DisplayName("expireIfNeeded(user): banUntil hala gelecekte → ban dokunulmaz")
    void expireIfNeeded_futureExpiry_keepBan() {
        Instant future = Instant.now().plusSeconds(3600);
        AdminUserView u = view("u1", BanStatus.TEMPORARY_BANNED, future);

        assertThat(service.expireIfNeeded(u)).isSameAs(u);
        verifyNoInteractions(unbanService);
    }

    @Test
    @DisplayName("expireIfNeeded(user): banUntil geçmişte → unbanlanır, fresh enriched user döner")
    void expireIfNeeded_pastExpiry_unbansAndReenriches() {
        Instant past = Instant.now().minusSeconds(3600);
        AdminUserView old = view("u1", BanStatus.TEMPORARY_BANNED, past);
        AdminUserView refreshed = view("u1", BanStatus.ACTIVE, null);
        when(keycloak.getUser("u1")).thenReturn(refreshed);
        when(enrichment.enrichUser(refreshed)).thenReturn(refreshed);

        AdminUserView out = service.expireIfNeeded(old);

        verify(unbanService).unbanUser("u1");
        verify(keycloak).getUser("u1");
        verify(enrichment).enrichUser(refreshed);
        assertThat(out.getBanStatus()).isEqualTo(BanStatus.ACTIVE);
    }

    // ------------------------------ expireIfNeeded (list) ------------------------------

    @Test
    @DisplayName("expireIfNeeded(list): her kullanıcıya tek tek uygulanır")
    void expireIfNeeded_list_appliesPerUser() {
        AdminUserView ok = view("u1", BanStatus.ACTIVE, null);
        AdminUserView banned = view("u2", BanStatus.PERMANENT_BANNED, null);

        List<AdminUserView> out = service.expireIfNeeded(List.of(ok, banned));

        assertThat(out).extracting(AdminUserView::getId).containsExactly("u1", "u2");
        verifyNoInteractions(unbanService);
    }

    private static AdminUserView view(String id, BanStatus status, Instant banUntil) {
        return new AdminUserView(
                id, id, id + "@x.com", "F", "L",
                true, status == BanStatus.ACTIVE,
                List.of(), banUntil, false, status, null);
    }
}
