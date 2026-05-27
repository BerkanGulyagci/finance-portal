package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserListResult;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.presentation.dto.AdminBanStatusFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AdminUserQueryServiceTest {

    private KeycloakUserAdminPort keycloak;
    private AdminUserEnrichmentService enrichment;
    private TemporaryBanExpiryService banExpiry;
    private AdminUserQueryService service;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakUserAdminPort.class);
        enrichment = mock(AdminUserEnrichmentService.class);
        banExpiry = mock(TemporaryBanExpiryService.class);
        // enrichment + banExpiry default: passthrough
        when(enrichment.enrichUsers(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrichment.enrichUser(any())).thenAnswer(inv -> inv.getArgument(0));
        when(banExpiry.expireIfNeeded(any(List.class))).thenAnswer(inv -> inv.getArgument(0));
        when(banExpiry.expireIfNeeded(any(AdminUserView.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AdminUserQueryService(keycloak, enrichment, banExpiry);
    }

    // ============================================================================
    // pagination validation
    // ============================================================================

    @Test
    @DisplayName("listUsers: first < 0 → IllegalArgumentException")
    void listUsers_negativeFirst_rejected() {
        assertThatThrownBy(() -> service.listUsers(null, -1, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'first'");
    }

    @Test
    @DisplayName("listUsers: max < 1 veya > 100 → IllegalArgumentException")
    void listUsers_invalidMax_rejected() {
        assertThatThrownBy(() -> service.listUsers(null, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'max'");
        assertThatThrownBy(() -> service.listUsers(null, 0, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'max'");
    }

    // ============================================================================
    // listUsers — filter=ALL
    // ============================================================================

    @Test
    @DisplayName("listUsers ALL: hasMore=true ise son eleman atılıp döner")
    void listUsers_all_paginatesWithHasMore() {
        // Test max=2 ile çağrı yaparsa KC'ye max+1=3 istek atar
        when(keycloak.listUsers(null, 0, 3))
                .thenReturn(List.of(user("u1"), user("u2"), user("u3")));

        AdminUserListResult r = service.listUsers(null, 0, 2, AdminBanStatusFilter.ALL);

        assertThat(r.getUsers()).hasSize(2);
        assertThat(r.getUsers()).extracting(AdminUserView::getId).containsExactly("u1", "u2");
        assertThat(r.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("listUsers ALL: dönen <= max ise hasMore=false")
    void listUsers_all_lessThanMax_noMore() {
        when(keycloak.listUsers(null, 0, 11))
                .thenReturn(List.of(user("u1"), user("u2")));

        AdminUserListResult r = service.listUsers(null, 0, 10, null);  // null → ALL

        assertThat(r.getUsers()).hasSize(2);
        assertThat(r.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("listUsers: search trim'lenir; boşsa null gönderilir")
    void listUsers_searchNormalized() {
        when(keycloak.listUsers(null, 0, 11)).thenReturn(List.of());

        service.listUsers("   ", 0, 10, null);

        verify(keycloak).listUsers(null, 0, 11);

        reset(keycloak);
        when(keycloak.listUsers("berkan", 0, 11)).thenReturn(List.of());

        service.listUsers("  berkan  ", 0, 10, null);

        verify(keycloak).listUsers("berkan", 0, 11);
    }

    @Test
    @DisplayName("listUsers ALL: enrichment + banExpiry her çağrıda uygulanır")
    void listUsers_all_appliesEnrichmentAndExpiry() {
        when(keycloak.listUsers(any(), anyInt(), anyInt())).thenReturn(List.of(user("u1")));

        service.listUsers(null, 0, 10, AdminBanStatusFilter.ALL);

        verify(enrichment).enrichUsers(any());
        verify(banExpiry).expireIfNeeded(any(List.class));
    }

    // ============================================================================
    // listUsers — filter=ACTIVE / BANNED
    // ============================================================================

    @Test
    @DisplayName("listUsers ACTIVE: yalnız BanStatus.ACTIVE olanlar dönülür")
    void listUsers_active_filterApplied() {
        AdminUserView active = userWithStatus("a", BanStatus.ACTIVE);
        AdminUserView banned = userWithStatus("b", BanStatus.PERMANENT_BANNED);
        AdminUserView active2 = userWithStatus("c", BanStatus.ACTIVE);
        // KC scan: bir batch'te 3 user
        when(keycloak.listUsers(any(), eq(0), anyInt()))
                .thenReturn(List.of(active, banned, active2));

        AdminUserListResult r = service.listUsers(null, 0, 10, AdminBanStatusFilter.ACTIVE);

        assertThat(r.getUsers()).extracting(AdminUserView::getId).containsExactly("a", "c");
    }

    @Test
    @DisplayName("listUsers BANNED: yalnız BanStatus.ACTIVE olmayanlar dönülür")
    void listUsers_banned_filterApplied() {
        AdminUserView active = userWithStatus("a", BanStatus.ACTIVE);
        AdminUserView banned = userWithStatus("b", BanStatus.PERMANENT_BANNED);
        when(keycloak.listUsers(any(), eq(0), anyInt()))
                .thenReturn(List.of(active, banned));

        AdminUserListResult r = service.listUsers(null, 0, 10, AdminBanStatusFilter.BANNED);

        assertThat(r.getUsers()).extracting(AdminUserView::getId).containsExactly("b");
    }

    @Test
    @DisplayName("listUsers ACTIVE: first=N ile sayfalama (skip mekanizması)")
    void listUsers_active_paginatesWithSkip() {
        AdminUserView a = userWithStatus("a", BanStatus.ACTIVE);
        AdminUserView b = userWithStatus("b", BanStatus.ACTIVE);
        AdminUserView c = userWithStatus("c", BanStatus.ACTIVE);
        when(keycloak.listUsers(any(), eq(0), anyInt()))
                .thenReturn(List.of(a, b, c));

        // first=1 → ilk 1 ACTIVE atlanır, sonraki 2 dönülür
        AdminUserListResult r = service.listUsers(null, 1, 2, AdminBanStatusFilter.ACTIVE);

        assertThat(r.getUsers()).extracting(AdminUserView::getId).containsExactly("b", "c");
    }

    // ============================================================================
    // getUser / listUsersByIds
    // ============================================================================

    @Test
    @DisplayName("getUser: KC fetch + enrichment + banExpiry zinciri")
    void getUser_chain() {
        AdminUserView raw = user("u1");
        when(keycloak.getUser("u1")).thenReturn(raw);

        AdminUserView out = service.getUser("u1");

        assertThat(out).isSameAs(raw);
        verify(enrichment).enrichUser(raw);
        verify(banExpiry).expireIfNeeded(raw);
    }

    @Test
    @DisplayName("listUsersByIds: bulunabilen kullanıcılar dönülür, bulunamayanlar atlanır")
    void listUsersByIds_skipsFailures() {
        when(keycloak.getUser("ok1")).thenReturn(user("ok1"));
        when(keycloak.getUser("notfound")).thenThrow(new RuntimeException("not found"));
        when(keycloak.getUser("ok2")).thenReturn(user("ok2"));

        AdminUserListResult r = service.listUsersByIds(List.of("ok1", "notfound", "ok2"));

        assertThat(r.getUsers()).extracting(AdminUserView::getId).containsExactly("ok1", "ok2");
        assertThat(r.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("listUsersByIds: boş liste → boş sonuç")
    void listUsersByIds_emptyInput_emptyResult() {
        AdminUserListResult r = service.listUsersByIds(List.of());

        assertThat(r.getUsers()).isEmpty();
        verifyNoInteractions(keycloak);
    }

    private static AdminUserView user(String id) {
        return userWithStatus(id, BanStatus.ACTIVE);
    }

    private static AdminUserView userWithStatus(String id, BanStatus status) {
        return new AdminUserView(
                id, id + "-username", id + "@example.com",
                "First", "Last",
                true, true,
                List.of(),
                null, false,
                status, null);
    }
}
