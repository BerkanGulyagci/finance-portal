package com.finance.portal.notification.application.service;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.domain.EmailStatus;
import com.finance.portal.notification.domain.Notification;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository repo;
    private EmailSenderPort emailSender;
    private CentralBusinessLogService businessLog;
    private NotificationService service;

    private static final String USER = "u1";
    private static final String EMAIL = "u1@example.com";

    @BeforeEach
    void setUp() {
        repo = mock(NotificationRepository.class);
        emailSender = mock(EmailSenderPort.class);
        businessLog = mock(CentralBusinessLogService.class);
        when(repo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });
        service = new NotificationService(repo, emailSender, businessLog);
    }

    // ============================================================================
    // createAndSend()
    // ============================================================================

    @Test
    @DisplayName("createAndSend: alıcı e-posta var, sender başarılı → status SENT, sentAt dolu")
    void createAndSend_emailSucceeds_statusSent() {
        Notification saved = service.createAndSend(
                USER, NotificationType.ALARM, "BTC tetiklendi",
                "BTC 50000'in altına indi", null, EMAIL, UUID.randomUUID());

        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getType()).isEqualTo(NotificationType.ALARM);
        assertThat(saved.getEmailStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.isRead()).isFalse();
        verify(emailSender).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("createAndSend: tür null verilirse varsayılan ALARM")
    void createAndSend_nullType_defaultsToAlarm() {
        Notification saved = service.createAndSend(
                USER, null, "Test", "Test body", null, EMAIL, null);

        assertThat(saved.getType()).isEqualTo(NotificationType.ALARM);
    }

    @Test
    @DisplayName("createAndSend: emailHtml verilirse HTML gövdesi e-postaya gider")
    void createAndSend_emailHtml_usedAsBody() {
        service.createAndSend(USER, NotificationType.NEWSLETTER, "Bülten",
                "Plain body", "<h1>HTML</h1>", EMAIL, null);

        verify(emailSender).send(EMAIL, "Bülten", "<h1>HTML</h1>");
    }

    @Test
    @DisplayName("createAndSend: emailHtml null ise plain body kullanılır")
    void createAndSend_noHtml_usesPlainBody() {
        service.createAndSend(USER, NotificationType.ALARM, "Başlık",
                "Düz gövde", null, EMAIL, null);

        verify(emailSender).send(EMAIL, "Başlık", "Düz gövde");
    }

    @Test
    @DisplayName("createAndSend: alıcı e-posta null → SKIPPED, sender çağrılmaz")
    void createAndSend_noRecipient_skipped() {
        Notification saved = service.createAndSend(
                USER, NotificationType.ALARM, "Test", "Body", null, null, null);

        assertThat(saved.getEmailStatus()).isEqualTo(EmailStatus.SKIPPED);
        assertThat(saved.getEmailError()).contains("yok veya doğrulanmamış");
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("createAndSend: alıcı e-posta boş string → SKIPPED")
    void createAndSend_blankRecipient_skipped() {
        Notification saved = service.createAndSend(
                USER, NotificationType.ALARM, "Test", "Body", null, "  ", null);

        assertThat(saved.getEmailStatus()).isEqualTo(EmailStatus.SKIPPED);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("createAndSend: sender exception fırlatırsa → FAILED, hata kaydedilir, bildirim yine kaydedilir")
    void createAndSend_emailFails_recordedAndPersisted() {
        doThrow(new RuntimeException("SMTP timeout"))
                .when(emailSender).send(any(), any(), any());

        Notification saved = service.createAndSend(
                USER, NotificationType.ALARM, "Test", "Body", null, EMAIL, null);

        assertThat(saved.getEmailStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(saved.getEmailError()).contains("SMTP timeout");
        // Bildirim yine de kaydedildi
        verify(repo).save(saved);
    }

    @Test
    @DisplayName("createAndSend: çok uzun hata mesajı 1000 karaktere kesilir")
    void createAndSend_veryLongError_truncated() {
        String huge = "x".repeat(2000);
        doThrow(new RuntimeException(huge))
                .when(emailSender).send(any(), any(), any());

        Notification saved = service.createAndSend(
                USER, NotificationType.ALARM, "Test", "Body", null, EMAIL, null);

        assertThat(saved.getEmailError()).hasSize(1000);
    }

    // ============================================================================
    // getUserNotifications / countUnread
    // ============================================================================

    @Test
    @DisplayName("getUserNotifications: createdAt desc, ilk 100 sayfa")
    void getUserNotifications_returnsRecent() {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        when(repo.findByUserIdOrderByCreatedAtDesc(eq(USER), any())).thenReturn(List.of(n));

        List<Notification> out = service.getUserNotifications(USER);

        assertThat(out).containsExactly(n);
    }

    @Test
    @DisplayName("countUnread: repo'ya devreder")
    void countUnread_delegatesToRepo() {
        when(repo.countByUserIdAndReadFalse(USER)).thenReturn(5L);

        assertThat(service.countUnread(USER)).isEqualTo(5L);
    }

    // ============================================================================
    // markRead / markAllRead / delete
    // ============================================================================

    @Test
    @DisplayName("markRead: bulunamaz → ResourceNotFoundException")
    void markRead_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(USER, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("markRead: zaten okunmuşsa save'i tekrar çağırmaz")
    void markRead_alreadyRead_noSave() {
        UUID id = UUID.randomUUID();
        Notification n = new Notification();
        n.setId(id);
        n.setRead(true);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(n));

        Notification out = service.markRead(USER, id);

        assertThat(out.isRead()).isTrue();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("markRead: okunmamışsa read=true + save")
    void markRead_unread_savesAsRead() {
        UUID id = UUID.randomUUID();
        Notification n = new Notification();
        n.setId(id);
        n.setRead(false);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(n));

        Notification out = service.markRead(USER, id);

        assertThat(out.isRead()).isTrue();
        verify(repo).save(n);
    }

    @Test
    @DisplayName("markAllRead: repo'nun bulk update'ine devreder, etkilenen satır sayısını döner")
    void markAllRead_returnsAffectedRows() {
        when(repo.markAllReadForUser(USER)).thenReturn(7);

        assertThat(service.markAllRead(USER)).isEqualTo(7);
    }

    @Test
    @DisplayName("delete: bulunamaz → ResourceNotFoundException")
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete: bulununca repo.delete çağrılır")
    void delete_found_deletes() {
        UUID id = UUID.randomUUID();
        Notification n = new Notification();
        n.setId(id);
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(n));

        service.delete(USER, id);

        verify(repo).delete(n);
    }
}
