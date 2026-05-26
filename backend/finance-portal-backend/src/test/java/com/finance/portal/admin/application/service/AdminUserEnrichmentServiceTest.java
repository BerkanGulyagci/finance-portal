package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.UserBanStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminUserEnrichmentServiceTest {

    private UserBanStatePort banStatePort;
    private AdminUserEnrichmentService service;

    @BeforeEach
    void setUp() {
        banStatePort = mock(UserBanStatePort.class);
        service = new AdminUserEnrichmentService(banStatePort);
    }

    // ------------------------------ enrichUsers ------------------------------

    @Test
    @DisplayName("enrichUsers: boş liste → aynen döner, port'a gitmez")
    void enrichUsers_empty_returnsSameList() {
        List<AdminUserView> empty = List.of();
        assertThat(service.enrichUsers(empty)).isSameAs(empty);
        verifyNoInteractions(banStatePort);
    }

    @Test
    @DisplayName("enrichUsers: ban state olmayan kullanıcı → ACTIVE, enabled=true")
    void enrichUsers_noBanState_active() {
        AdminUserView u = view("u1", true);
        when(banStatePort.findByUserIds(List.of("u1"))).thenReturn(Map.of());

        List<AdminUserView> out = service.enrichUsers(List.of(u));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getBanStatus()).isEqualTo(BanStatus.ACTIVE);
        assertThat(out.get(0).getBanUntil()).isNull();
        assertThat(out.get(0).isPermanentBan()).isFalse();
    }

    @Test
    @DisplayName("enrichUsers: permanent ban state → PERMANENT_BANNED, permanentBan=true")
    void enrichUsers_permanentBan() {
        AdminUserView u = view("u1", false);
        UserBanState state = mock(UserBanState.class);
        when(state.isPermanent()).thenReturn(true);
        when(state.getBanUntil()).thenReturn(null);
        when(state.getBanReason()).thenReturn("spam");
        when(banStatePort.findByUserIds(List.of("u1"))).thenReturn(Map.of("u1", state));

        List<AdminUserView> out = service.enrichUsers(List.of(u));

        assertThat(out.get(0).getBanStatus()).isEqualTo(BanStatus.PERMANENT_BANNED);
        assertThat(out.get(0).isPermanentBan()).isTrue();
        assertThat(out.get(0).getBanReason()).isEqualTo("spam");
    }

    @Test
    @DisplayName("enrichUsers: temporary ban state → TEMPORARY_BANNED, banUntil dolu")
    void enrichUsers_temporaryBan() {
        AdminUserView u = view("u1", false);
        Instant until = Instant.now().plusSeconds(3600);
        UserBanState state = mock(UserBanState.class);
        when(state.isPermanent()).thenReturn(false);
        when(state.getBanUntil()).thenReturn(until);
        when(banStatePort.findByUserIds(List.of("u1"))).thenReturn(Map.of("u1", state));

        List<AdminUserView> out = service.enrichUsers(List.of(u));

        assertThat(out.get(0).getBanStatus()).isEqualTo(BanStatus.TEMPORARY_BANNED);
        assertThat(out.get(0).getBanUntil()).isEqualTo(until);
        assertThat(out.get(0).isPermanentBan()).isFalse();
    }

    @Test
    @DisplayName("enrichUsers: bir kullanıcının ban'ı var diğerinin yok → her ikisine ayrı ayrı uygulanır")
    void enrichUsers_mixedStates() {
        AdminUserView u1 = view("u1", true);
        AdminUserView u2 = view("u2", false);
        UserBanState s = mock(UserBanState.class);
        when(s.isPermanent()).thenReturn(true);
        when(banStatePort.findByUserIds(List.of("u1", "u2"))).thenReturn(Map.of("u2", s));

        List<AdminUserView> out = service.enrichUsers(List.of(u1, u2));

        assertThat(out.get(0).getBanStatus()).isEqualTo(BanStatus.ACTIVE);
        assertThat(out.get(1).getBanStatus()).isEqualTo(BanStatus.PERMANENT_BANNED);
    }

    // ------------------------------ enrichUser ------------------------------

    @Test
    @DisplayName("enrichUser: ban state yok → ACTIVE")
    void enrichUser_noBan_active() {
        AdminUserView u = view("u1", true);
        when(banStatePort.findByUserId("u1")).thenReturn(Optional.empty());

        AdminUserView out = service.enrichUser(u);

        assertThat(out.getBanStatus()).isEqualTo(BanStatus.ACTIVE);
    }

    @Test
    @DisplayName("enrichUser: permanent ban → PERMANENT_BANNED")
    void enrichUser_permanent() {
        AdminUserView u = view("u1", false);
        UserBanState state = mock(UserBanState.class);
        when(state.isPermanent()).thenReturn(true);
        when(banStatePort.findByUserId("u1")).thenReturn(Optional.of(state));

        AdminUserView out = service.enrichUser(u);

        assertThat(out.getBanStatus()).isEqualTo(BanStatus.PERMANENT_BANNED);
    }

    private static AdminUserView view(String id, boolean enabled) {
        return new AdminUserView(
                id, id + "-user", id + "@example.com", "First", "Last",
                true, enabled, List.of(), null, false, BanStatus.ACTIVE, null);
    }
}
