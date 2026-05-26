package com.finance.portal.support.application.service;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.domain.SupportTicketStatus;
import com.finance.portal.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SupportTicketServiceTest {

    private SupportTicketRepository repo;
    private NotificationService notifications;
    private KeycloakUserAdminPort keycloak;
    private CentralBusinessLogService businessLog;
    private SupportTicketService service;

    private static final String USER = "u1";
    private static final String EMAIL = "u1@example.com";

    @BeforeEach
    void setUp() {
        repo = mock(SupportTicketRepository.class);
        notifications = mock(NotificationService.class);
        keycloak = mock(KeycloakUserAdminPort.class);
        businessLog = mock(CentralBusinessLogService.class);
        when(repo.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        // admin list default empty
        when(keycloak.findUsersByRealmRole(anyString())).thenReturn(List.of());
        service = new SupportTicketService(repo, notifications, keycloak, businessLog);
    }

    // ============================================================================
    // create()
    // ============================================================================

    @Test
    @DisplayName("create: happy path → ticket OPEN, audit + admin bildirimi")
    void create_happyPath() {
        when(repo.countByUserIdAndStatusNot(USER, SupportTicketStatus.RESOLVED)).thenReturn(0L);

        SupportTicket out = service.create(USER, EMAIL, "Berkan",
                "Login problem", "Cannot login since today");

        assertThat(out.getUserId()).isEqualTo(USER);
        assertThat(out.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(out.getSubject()).isEqualTo("Login problem");
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create: konu/açıklama trim'lenir; sadece boşluk → 'zorunlu'")
    void create_blankSubjectOrMessage_rejected() {
        assertThatThrownBy(() -> service.create(USER, EMAIL, "name", "   ", "msg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zorunlu");
        assertThatThrownBy(() -> service.create(USER, EMAIL, "name", "subj", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zorunlu");
    }

    @Test
    @DisplayName("create: çok uzun konu (>200) ve mesaj (>5000) kesilir")
    void create_tooLong_truncated() {
        when(repo.countByUserIdAndStatusNot(any(), any())).thenReturn(0L);
        String longSubject = "x".repeat(300);
        String longMessage = "y".repeat(7000);

        SupportTicket out = service.create(USER, EMAIL, "name", longSubject, longMessage);

        assertThat(out.getSubject()).hasSize(200);
        assertThat(out.getMessage()).hasSize(5000);
    }

    @Test
    @DisplayName("create: 3 açık talep varsa yenisi reddedilir")
    void create_maxActiveTickets_rejected() {
        when(repo.countByUserIdAndStatusNot(USER, SupportTicketStatus.RESOLVED)).thenReturn(3L);

        assertThatThrownBy(() -> service.create(USER, EMAIL, "name", "subj", "msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en fazla 3");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("create: admin'ler bildirilir, talebi açan admin'in kendisi atlanır")
    void create_notifiesOtherAdmins_skipsSelf() {
        when(repo.countByUserIdAndStatusNot(USER, SupportTicketStatus.RESOLVED)).thenReturn(0L);
        com.finance.portal.admin.application.model.AdminUserView a1 = mock(com.finance.portal.admin.application.model.AdminUserView.class);
        when(a1.getId()).thenReturn("admin-other");
        com.finance.portal.admin.application.model.AdminUserView a2 = mock(com.finance.portal.admin.application.model.AdminUserView.class);
        when(a2.getId()).thenReturn(USER);  // talebi açan kullanıcının kendisi (admin)
        when(keycloak.findUsersByRealmRole("ADMIN")).thenReturn(List.of(a1, a2));

        service.create(USER, EMAIL, "Berkan", "subj", "msg");

        // Yalnız "admin-other"a bildirim
        verify(notifications).createAndSend(eq("admin-other"), eq(NotificationType.SUPPORT),
                any(), any(), any(), eq(null), eq(null));
        verify(notifications, never()).createAndSend(eq(USER), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create: admin liste hatası → talep yine oluşur (graceful)")
    void create_adminLookupFails_stillCreatesTicket() {
        when(repo.countByUserIdAndStatusNot(USER, SupportTicketStatus.RESOLVED)).thenReturn(0L);
        when(keycloak.findUsersByRealmRole(anyString()))
                .thenThrow(new RuntimeException("KC down"));

        SupportTicket out = service.create(USER, EMAIL, null, "subj", "msg");

        assertThat(out.getId()).isNotNull();
        assertThat(out.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
    }

    // ============================================================================
    // update / delete (user)
    // ============================================================================

    @Test
    @DisplayName("update: OPEN durumda → konu/mesaj güncellenir")
    void update_open_modifies() {
        UUID id = UUID.randomUUID();
        SupportTicket existing = openTicket(id);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(existing));

        SupportTicket out = service.update(USER, id, "Yeni konu", "Yeni mesaj");

        assertThat(out.getSubject()).isEqualTo("Yeni konu");
        assertThat(out.getMessage()).isEqualTo("Yeni mesaj");
    }

    @Test
    @DisplayName("update: IN_PROGRESS / RESOLVED durumlarda düzenlenemez")
    void update_nonOpen_rejected() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        t.setStatus(SupportTicketStatus.IN_PROGRESS);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.update(USER, id, "yeni", "yeni"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("işleme alındıktan sonra");
    }

    @Test
    @DisplayName("update: bulunamaz → ResourceNotFoundException")
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, id, "x", "y"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete: OPEN durumda → silinir")
    void delete_open_succeeds() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(t));

        service.delete(USER, id);

        verify(repo).delete(t);
    }

    @Test
    @DisplayName("delete: işleme alınmış talep silinemez")
    void delete_nonOpen_rejected() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        t.setStatus(SupportTicketStatus.RESOLVED);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.delete(USER, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("İşleme alınmış");
    }

    // ============================================================================
    // updateStatus (admin)
    // ============================================================================

    @Test
    @DisplayName("updateStatus: durum değişince kullanıcı bildirilir (IN_PROGRESS → 'işleme alındı')")
    void updateStatus_inProgress_notifiesUser() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        when(repo.findById(id)).thenReturn(Optional.of(t));

        SupportTicket out = service.updateStatus(id, SupportTicketStatus.IN_PROGRESS, "Yardımcı oluyoruz");

        assertThat(out.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        verify(notifications).createAndSend(eq(USER), eq(NotificationType.SUPPORT),
                titleCap.capture(), any(), any(), eq(EMAIL), any());
        assertThat(titleCap.getValue()).contains("işleme alındı");
    }

    @Test
    @DisplayName("updateStatus: RESOLVED → 'çözüldü' bildirimi")
    void updateStatus_resolved_notifiesUser() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        when(repo.findById(id)).thenReturn(Optional.of(t));

        service.updateStatus(id, SupportTicketStatus.RESOLVED, null);

        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        verify(notifications).createAndSend(any(), any(), titleCap.capture(),
                any(), any(), any(), any());
        assertThat(titleCap.getValue()).contains("çözüldü");
    }

    @Test
    @DisplayName("updateStatus: durum aynı kalırsa kullanıcı bildirilmez (gereksiz bildirim önlenir)")
    void updateStatus_sameStatus_noNotification() {
        UUID id = UUID.randomUUID();
        SupportTicket t = openTicket(id);
        t.setStatus(SupportTicketStatus.IN_PROGRESS);
        when(repo.findById(id)).thenReturn(Optional.of(t));

        service.updateStatus(id, SupportTicketStatus.IN_PROGRESS, "ek not");

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("updateStatus: null durum → 'Geçersiz durum'")
    void updateStatus_nullStatus_rejected() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(openTicket(id)));

        assertThatThrownBy(() -> service.updateStatus(id, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geçersiz");
    }

    @Test
    @DisplayName("updateStatus: bulunamaz → ResourceNotFoundException")
    void updateStatus_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, SupportTicketStatus.RESOLVED, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================================
    // list / count helpers
    // ============================================================================

    @Test
    @DisplayName("listForUser / listForUserAsAdmin: repo'ya createdAt desc ile devreder")
    void list_passesThrough() {
        SupportTicket t = openTicket(UUID.randomUUID());
        when(repo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(t));

        assertThat(service.listForUser(USER)).containsExactly(t);
        assertThat(service.listForUserAsAdmin(USER)).containsExactly(t);
    }

    @Test
    @DisplayName("userIdsWithActiveTickets / activeTicketCountByUser: repo'ya devreder")
    void adminHelpers_delegate() {
        when(repo.findUserIdsWithActiveTickets()).thenReturn(List.of("u1", "u2"));
        when(repo.countActiveGroupedByUser()).thenReturn(List.of(
                new Object[]{"u1", 2L},
                new Object[]{"u2", 1L}));

        assertThat(service.userIdsWithActiveTickets()).containsExactly("u1", "u2");
        assertThat(service.activeTicketCountByUser())
                .containsEntry("u1", 2L)
                .containsEntry("u2", 1L);
    }

    // ============================================================================
    // helper
    // ============================================================================

    private static SupportTicket openTicket(UUID id) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        t.setUserId(USER);
        t.setUserEmail(EMAIL);
        t.setSubject("Eski konu");
        t.setMessage("Eski mesaj");
        t.setStatus(SupportTicketStatus.OPEN);
        return t;
    }
}
