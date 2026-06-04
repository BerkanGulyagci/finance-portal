package com.finance.portal.newsletter.application.service;

import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.newsletter.application.model.DigestData;
import com.finance.portal.newsletter.application.port.NewsletterDigestPort;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsletterDigestServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    @Mock
    private NewsletterSubscriptionRepository repository;
    @Mock
    private NewsletterDigestPort digestPort;
    @Mock
    private EmailSenderPort emailSender;
    @Mock
    private CentralBusinessLogService businessLog;
    @Mock
    private NotificationService notificationService;

    private NewsletterDigestService service;

    @BeforeEach
    void setUp() {
        service = new NewsletterDigestService(repository, digestPort, emailSender, businessLog,
                notificationService, true, "http://test.local");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private NewsletterSubscription sub(String userId, String email, NewsletterFrequency freq,
                                       LocalDateTime lastSent) {
        NewsletterSubscription s = new NewsletterSubscription();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setEmail(email);
        s.setFrequency(freq);
        s.setEnabled(true);
        s.setUnsubscribeToken("tok-" + userId);
        s.setLastSentAt(lastSent);
        return s;
    }

    private DigestData fullDigest() {
        return new DigestData(
                2,
                new BigDecimal("125000.50"),
                new BigDecimal("5000.00"),
                new BigDecimal("4.17"),
                new BigDecimal("-1.25"),
                List.of(new DigestData.Slice("Hisse", new BigDecimal("60.0")),
                        new DigestData.Slice("Kripto", new BigDecimal("40.0"))),
                List.of(new DigestData.PortfolioLine("Ana Portföy", new BigDecimal("100000"),
                        new BigDecimal("3000"), new BigDecimal("3.1"))),
                List.of(new DigestData.Mover("THYAO", "Türk Hava Yolları", new BigDecimal("5.5"))),
                List.of(new DigestData.Mover("BTC", "Bitcoin", new BigDecimal("-3.2"))),
                List.of(new DigestData.TxLine("THYAO", "BUY", new BigDecimal("2500"),
                        LocalDateTime.of(2026, 5, 20, 10, 0), "Ana Portföy")),
                List.of(new DigestData.Fav("AAPL", "Hisse", new BigDecimal("190.50"),
                        new BigDecimal("1.2"))),
                new DigestData.Market(new BigDecimal("32.50"), new BigDecimal("9500"),
                        new BigDecimal("2450.75"), new BigDecimal("38.5"), new BigDecimal("45.0")));
    }

    private DigestData emptyDigest() {
        return new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new DigestData.Market(null, null, null, null, null));
    }

    private LocalDate nextMonday() {
        return LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    // ── enabled flag ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendDueDigests: enabled=false → hiçbir şey yapılmaz")
    void disabledDoesNothing() {
        service = new NewsletterDigestService(repository, digestPort, emailSender, businessLog,
                notificationService, false, "http://test.local");

        service.sendDueDigests();

        verifyNoInteractions(repository, digestPort, emailSender, notificationService);
    }

    // ── DAILY due ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DAILY abonelik → digest gönderilir, lastSentAt güncellenir, log + bildirim atılır")
    void dailySendsDigest() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(fullDigest());

        service.sendDueDigests();

        ArgumentCaptor<String> toCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(toCap.capture(), subjCap.capture(), bodyCap.capture());

        assertThat(toCap.getValue()).isEqualTo("u1@x.com");
        assertThat(subjCap.getValue()).contains("FinansPortalı");
        String html = bodyCap.getValue();
        assertThat(html).contains("Panonuzun Özeti");
        assertThat(html).contains("Portföy Dağılımı");
        assertThat(html).contains("Portföylerim");
        assertThat(html).contains("Favoriler");
        assertThat(html).contains("Öne Çıkanlar");
        assertThat(html).contains("Son İşlemler");
        assertThat(html).contains("Piyasa");
        // unsubscribe link uses baseUrl + token
        assertThat(html).contains("http://test.local/api/v1/newsletter/unsubscribe?token=tok-u1");
        assertThat(html).contains("http://test.local/dashboard");

        assertThat(s.getLastSentAt()).isNotNull();
        verify(repository).save(s);
        verify(businessLog).publish(anyString(), anyString(), eq("INFO"), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), eq("u1"), anyString());
        verify(notificationService).createAndSend(eq("u1"), eq(NotificationType.NEWSLETTER),
                anyString(), anyString(), isNull(), isNull(), isNull());
    }

    // ── frequency due-logic ──────────────────────────────────────────────────────

    @Test
    @DisplayName("WEEKLY: bugün Pazartesi değilse gönderilmez")
    void weeklyNotMonday() {
        LocalDate today = LocalDate.now(ZONE);
        // pick a frequency/date guaranteed not due: WEEKLY only fires on Monday
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            // on Monday WEEKLY is due — skip the negative assertion gracefully
            return;
        }
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.WEEKLY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));

        service.sendDueDigests();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("MONTHLY: ayın 1'i değilse gönderilmez")
    void monthlyNotFirst() {
        LocalDate today = LocalDate.now(ZONE);
        if (today.getDayOfMonth() == 1) {
            return; // ayın 1'inde MONTHLY due — negatif iddia atlanır
        }
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.MONTHLY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));

        service.sendDueDigests();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Bugün zaten gönderildiyse (lastSentAt = bugün) tekrar gönderilmez")
    void alreadySentTodaySkipped() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY,
                LocalDateTime.now(ZONE));
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));

        service.sendDueDigests();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(digestPort, never()).buildFor(anyString());
    }

    @Test
    @DisplayName("DAILY: lastSentAt dün ise tekrar gönderilir")
    void dailyResendsNextDay() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY,
                LocalDateTime.now(ZONE).minusDays(1));
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(emptyDigest());

        service.sendDueDigests();

        verify(emailSender).send(eq("u1@x.com"), anyString(), anyString());
    }

    // ── email skipping ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("E-posta null/blank olan abonelik atlanır")
    void blankEmailSkipped() {
        NewsletterSubscription noEmail = sub("u1", null, NewsletterFrequency.DAILY, null);
        NewsletterSubscription blank = sub("u2", "  ", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(noEmail, blank));

        service.sendDueDigests();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(digestPort, never()).buildFor(anyString());
    }

    // ── exception isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Bir abonenin digest'i hata fırlatırsa diğerleri etkilenmez (try/catch izolasyonu)")
    void failureIsolatedPerUser() {
        NewsletterSubscription bad = sub("bad", "bad@x.com", NewsletterFrequency.DAILY, null);
        NewsletterSubscription good = sub("good", "good@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(bad, good));
        when(digestPort.buildFor("bad")).thenThrow(new RuntimeException("boom"));
        when(digestPort.buildFor("good")).thenReturn(emptyDigest());

        service.sendDueDigests();

        // good still sent
        verify(emailSender).send(eq("good@x.com"), anyString(), anyString());
        // bad never reached email
        verify(emailSender, never()).send(eq("bad@x.com"), anyString(), anyString());
        // bad: no save, no notification
        verify(repository, never()).save(bad);
    }

    @Test
    @DisplayName("emailSender.send hata fırlatırsa save/log/notification çalışmaz, akış sürer")
    void emailSendFailureHandled() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(emptyDigest());
        org.mockito.Mockito.doThrow(new EmailSenderPort.EmailSendException("smtp down", null))
                .when(emailSender).send(anyString(), anyString(), anyString());

        service.sendDueDigests();

        verify(repository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(),
                anyString(), any(), any(), any());
    }

    // ── empty digest renders without optional cards ──────────────────────────────

    @Test
    @DisplayName("Boş digest: opsiyonel kartlar render edilmez ama Piyasa + footer her zaman var")
    void emptyDigestOmitsOptionalCards() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(emptyDigest());

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        service.sendDueDigests();
        verify(emailSender).send(anyString(), anyString(), bodyCap.capture());

        String html = bodyCap.getValue();
        assertThat(html).doesNotContain("Portföy Dağılımı");
        assertThat(html).doesNotContain("Portföylerim");
        assertThat(html).doesNotContain("Favoriler");
        assertThat(html).doesNotContain("Öne Çıkanlar");
        assertThat(html).doesNotContain("Son İşlemler");
        // market card always present; null fields render em-dash placeholders / are skipped
        assertThat(html).contains("Piyasa");
        assertThat(html).contains("Panoyu Aç");
    }

    @Test
    @DisplayName("Boş abonelik listesi → mail gönderilmez")
    void noSubscribers() {
        when(repository.findByEnabledTrue()).thenReturn(List.of());

        service.sendDueDigests();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("HTML: özel karakterler escape edilir (XSS koruması)")
    void htmlEscaping() {
        NewsletterSubscription s = sub("u1", "u1@x.com", NewsletterFrequency.DAILY, null);
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new DigestData.Slice("<script>&", new BigDecimal("100"))),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new DigestData.Market(null, null, null, null, null));
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(d);

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        service.sendDueDigests();
        verify(emailSender).send(anyString(), anyString(), bodyCap.capture());

        String html = bodyCap.getValue();
        assertThat(html).contains("&lt;script&gt;&amp;");
        assertThat(html).doesNotContain("<script>&amp");
    }
}
