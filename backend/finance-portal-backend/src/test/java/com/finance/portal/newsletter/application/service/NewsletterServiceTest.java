package com.finance.portal.newsletter.application.service;

import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NewsletterServiceTest {

    private NewsletterSubscriptionRepository repo;
    private CentralBusinessLogService businessLog;
    private NewsletterService service;

    private static final String USER = "u1";
    private static final String EMAIL = "u1@example.com";

    @BeforeEach
    void setUp() {
        repo = mock(NewsletterSubscriptionRepository.class);
        businessLog = mock(CentralBusinessLogService.class);
        when(repo.save(any(NewsletterSubscription.class))).thenAnswer(inv -> {
            NewsletterSubscription s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        service = new NewsletterService(repo, businessLog);
    }

    // ============================================================================
    // upsert
    // ============================================================================

    @Test
    @DisplayName("upsert: kullanıcının kaydı yok → yeni kayıt, audit log atılır")
    void upsert_newSubscription_createsAndAudits() {
        when(repo.findByUserId(USER)).thenReturn(Optional.empty());

        NewsletterSubscription out = service.upsert(USER, EMAIL,
                NewsletterFrequency.DAILY, true);

        assertThat(out.getUserId()).isEqualTo(USER);
        assertThat(out.getEmail()).isEqualTo(EMAIL);
        assertThat(out.getFrequency()).isEqualTo(NewsletterFrequency.DAILY);
        assertThat(out.isEnabled()).isTrue();
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upsert: mevcut kayıt güncellenir, e-posta verildiyse değişir")
    void upsert_existing_updatesFields() {
        NewsletterSubscription existing = new NewsletterSubscription();
        existing.setUserId(USER);
        existing.setEmail("old@example.com");
        existing.setFrequency(NewsletterFrequency.WEEKLY);
        existing.setEnabled(false);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(existing));

        NewsletterSubscription out = service.upsert(USER, "new@example.com",
                NewsletterFrequency.DAILY, true);

        assertThat(out.getEmail()).isEqualTo("new@example.com");
        assertThat(out.getFrequency()).isEqualTo(NewsletterFrequency.DAILY);
        assertThat(out.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("upsert: e-posta null veya boşsa mevcut e-posta korunur")
    void upsert_nullEmail_keepsExisting() {
        NewsletterSubscription existing = new NewsletterSubscription();
        existing.setUserId(USER);
        existing.setEmail("kept@example.com");
        when(repo.findByUserId(USER)).thenReturn(Optional.of(existing));

        NewsletterSubscription out = service.upsert(USER, "  ",
                NewsletterFrequency.MONTHLY, true);

        assertThat(out.getEmail()).isEqualTo("kept@example.com");
    }

    @Test
    @DisplayName("upsert: frequency null verilirse mevcut frekans korunur")
    void upsert_nullFrequency_keepsExisting() {
        NewsletterSubscription existing = new NewsletterSubscription();
        existing.setUserId(USER);
        existing.setFrequency(NewsletterFrequency.WEEKLY);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(existing));

        NewsletterSubscription out = service.upsert(USER, EMAIL, null, true);

        assertThat(out.getFrequency()).isEqualTo(NewsletterFrequency.WEEKLY);
    }

    @Test
    @DisplayName("upsert: enabled=false → audit event 'unsubscribed'")
    void upsert_disable_auditUnsubscribed() {
        when(repo.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, EMAIL, NewsletterFrequency.DAILY, false);

        // 6. argümandaki "action" SUBSCRIBE veya UNSUBSCRIBE: UNSUBSCRIBE bekleniyor
        ArgumentCaptor<String> actionCap = ArgumentCaptor.forClass(String.class);
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(),
                actionCap.capture(), any(), any(), any(), any());
        assertThat(actionCap.getValue()).containsIgnoringCase("UNSUBSCRIBE");
    }

    // ============================================================================
    // disableForUser (ban cascade)
    // ============================================================================

    @Test
    @DisplayName("disableForUser: aktif abonelik → enabled=false, disabledByBan=true, true döner")
    void disableForUser_activeSubscription_disablesIt() {
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setUserId(USER);
        sub.setEnabled(true);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(sub));

        boolean out = service.disableForUser(USER);

        assertThat(out).isTrue();
        assertThat(sub.isEnabled()).isFalse();
        assertThat(sub.isDisabledByBan()).isTrue();
        verify(repo).save(sub);
    }

    @Test
    @DisplayName("disableForUser: zaten kapalı → false, save çağrılmaz")
    void disableForUser_alreadyDisabled_returnsFalse() {
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setUserId(USER);
        sub.setEnabled(false);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(sub));

        boolean out = service.disableForUser(USER);

        assertThat(out).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("disableForUser: kayıt yok → false")
    void disableForUser_noSubscription_returnsFalse() {
        when(repo.findByUserId(USER)).thenReturn(Optional.empty());

        assertThat(service.disableForUser(USER)).isFalse();
    }

    @Test
    @DisplayName("disableForUser: blank userId → false, repo'ya gitmez")
    void disableForUser_blankUserId_returnsFalse() {
        assertThat(service.disableForUser("  ")).isFalse();
        assertThat(service.disableForUser(null)).isFalse();
        verifyNoInteractions(repo);
    }

    // ============================================================================
    // reenableForUser
    // ============================================================================

    @Test
    @DisplayName("reenableForUser: ban yüzünden kapalıyı yeniden açar")
    void reenableForUser_banDisabled_reenabled() {
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setUserId(USER);
        sub.setEnabled(false);
        sub.setDisabledByBan(true);
        when(repo.findByUserId(USER)).thenReturn(Optional.of(sub));

        boolean out = service.reenableForUser(USER);

        assertThat(out).isTrue();
        assertThat(sub.isEnabled()).isTrue();
        assertThat(sub.isDisabledByBan()).isFalse();
        verify(repo).save(sub);
    }

    @Test
    @DisplayName("reenableForUser: kullanıcı kendi kapatmışsa korunur (false)")
    void reenableForUser_userDisabled_preserved() {
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setUserId(USER);
        sub.setEnabled(false);
        sub.setDisabledByBan(false);  // kullanıcının kendi kapatması
        when(repo.findByUserId(USER)).thenReturn(Optional.of(sub));

        boolean out = service.reenableForUser(USER);

        assertThat(out).isFalse();
        assertThat(sub.isEnabled()).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("reenableForUser: blank userId → false")
    void reenableForUser_blank_returnsFalse() {
        assertThat(service.reenableForUser("")).isFalse();
    }

    // ============================================================================
    // unsubscribeByToken
    // ============================================================================

    @Test
    @DisplayName("unsubscribeByToken: token geçerli → enabled=false, true döner")
    void unsubscribeByToken_validToken_unsubscribes() {
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setUserId(USER);
        sub.setEnabled(true);
        when(repo.findByUnsubscribeToken("abc123")).thenReturn(Optional.of(sub));

        boolean out = service.unsubscribeByToken("abc123");

        assertThat(out).isTrue();
        assertThat(sub.isEnabled()).isFalse();
        verify(repo).save(sub);
    }

    @Test
    @DisplayName("unsubscribeByToken: token bulunamaz → false, kayıt yok")
    void unsubscribeByToken_invalidToken_returnsFalse() {
        when(repo.findByUnsubscribeToken("bad")).thenReturn(Optional.empty());

        assertThat(service.unsubscribeByToken("bad")).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("unsubscribeByToken: null/boş token → false")
    void unsubscribeByToken_nullOrBlank_returnsFalse() {
        assertThat(service.unsubscribeByToken(null)).isFalse();
        assertThat(service.unsubscribeByToken("  ")).isFalse();
        verifyNoInteractions(repo);
    }

    // ============================================================================
    // findByUserId
    // ============================================================================

    @Test
    @DisplayName("findByUserId: repo'ya devreder")
    void findByUserId_passesThrough() {
        NewsletterSubscription sub = new NewsletterSubscription();
        when(repo.findByUserId(USER)).thenReturn(Optional.of(sub));

        assertThat(service.findByUserId(USER)).contains(sub);
    }
}
