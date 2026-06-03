package com.finance.portal.newsletter.application.service;

import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.newsletter.application.model.DigestData;
import com.finance.portal.newsletter.application.port.NewsletterDigestPort;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.application.service.NotificationService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage focused tests for {@link NewsletterDigestService} that complement
 * {@code NewsletterDigestServiceTest}. Targets the render-helper null/zero/false arms
 * (txRow SELL + null date/portfolio, favRow null price/change/type, allocBar w<=0 skip +
 * empty fallback + null percent, signedMoney/escape null arms) and the calendar-gated
 * WEEKLY/MONTHLY due + frequency-label switch arms.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsletterDigestServiceMoreTest {

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

    private NewsletterSubscription mkSub(String userId, String email, NewsletterFrequency freq,
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

    private DigestData.Market emptyMarket() {
        return new DigestData.Market(null, null, null, null, null);
    }

    /** Captures the rendered HTML body for a single DAILY (always-due) subscription. */
    private String renderHtmlFor(DigestData data) {
        NewsletterSubscription s = mkSub("u1", "u1@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u1")).thenReturn(data);

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        service.sendDueDigests();
        verify(emailSender).send(anyString(), anyString(), bodyCap.capture());
        return bodyCap.getValue();
    }

    // ── txRow branches: SELL type + null date + null portfolioName ────────────────

    @Test
    @DisplayName("txRow: SELL işlemi + null tarih + null portföy adı → Satış/kırmızı, tarih ve portföy boş")
    void txRowSellWithNulls() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new DigestData.TxLine("BTC", "SELL", new BigDecimal("1500"), null, null)),
                List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Son İşlemler");
        // SELL arm → "Satış" label + red color, not "Alış"
        assertThat(html).contains("Satış");
        assertThat(html).doesNotContain("Alış");
        assertThat(html).contains("BTC");
    }

    @Test
    @DisplayName("txRow: BUY (büyük/küçük harf karışık) + tarih + portföy dolu → Alış arm ve tarih/portföy basılır")
    void txRowBuyMixedCaseWithDateAndPortfolio() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new DigestData.TxLine("THYAO", "buy", new BigDecimal("2500"),
                        LocalDateTime.of(2026, 5, 20, 10, 0), "Ana Portföy")),
                List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Alış");
        assertThat(html).contains("Ana Portföy");
    }

    // ── favRow branches: null lastPrice / null changePercent / null typeLabel ─────

    @Test
    @DisplayName("favRow: lastPrice/changePercent/typeLabel hepsi null → fiyat, değişim ve tür boş render")
    void favRowAllNullOptionalFields() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new DigestData.Fav("GOOGL", null, null, null)),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Favoriler");
        assertThat(html).contains("GOOGL");
    }

    // ── allocBar branches: w<=0 skip, empty fallback, null percent in detail table ─

    @Test
    @DisplayName("allocBar: tüm dilim yüzdeleri 0/null → çubuk boş fallback (#e5e7eb), detayda money1/escape null arm")
    void allocBarAllZeroFallbackAndNullPercent() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new DigestData.Slice("Nakit", BigDecimal.ZERO),
                        new DigestData.Slice(null, null)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Portföy Dağılımı");
        // no positive slice → empty-bar fallback color used
        assertThat(html).contains("#e5e7eb");
        // null-percent slice renders the em-dash from money1
        assertThat(html).contains("—");
    }

    @Test
    @DisplayName("allocBar: bir dilim pozitif, bir dilim negatif/0 → pozitif çizilir, negatif w<=0 atlanır")
    void allocBarMixedPositiveAndNonPositive() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new DigestData.Slice("Hisse", new BigDecimal("70.0")),
                        new DigestData.Slice("Zarar", new BigDecimal("-5.0")),
                        new DigestData.Slice("Sıfır", BigDecimal.ZERO)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Portföy Dağılımı");
        // positive slice drawn with explicit width, no empty fallback
        assertThat(html).contains("70.00%");
        assertThat(html).doesNotContain("#e5e7eb");
    }

    // ── PortfolioLine: null profitLoss/percent → sign(null), signedMoney(null), signedPct(null) ─

    @Test
    @DisplayName("Portföy satırı: profitLoss/percent null → sign null-arm (#059669) + signedMoney/signedPct em-dash")
    void portfolioLineNullProfit() {
        DigestData d = new DigestData(1, new BigDecimal("100"), null, null, null,
                List.of(),
                List.of(new DigestData.PortfolioLine("Boş Portföy", new BigDecimal("0"), null, null)),
                List.of(), List.of(), List.of(), List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Portföylerim");
        assertThat(html).contains("Boş Portföy");
        // signedMoney(null)/signedPct(null) produce em-dash
        assertThat(html).contains("—");
    }

    // ── Movers with null symbol/name → escape null-arm ───────────────────────────

    @Test
    @DisplayName("Öne Çıkanlar: gainer/loser symbol+name null → escape null-arm boş string döner (NPE yok)")
    void moversWithNullSymbolName() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(new DigestData.Mover(null, null, new BigDecimal("2.0"))),
                List.of(new DigestData.Mover(null, null, new BigDecimal("-2.0"))),
                List.of(), List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Öne Çıkanlar");
        // up + down arrows both rendered (gainer + loser arms)
        assertThat(html).contains("&#9650;");
        assertThat(html).contains("&#9660;");
    }

    // ── Öne Çıkanlar card present when only losers exist (|| second arm) ──────────

    @Test
    @DisplayName("Öne Çıkanlar: yalnızca losers dolu (gainers boş) → kart yine render edilir (|| ikinci kolu)")
    void highlightsOnlyLosers() {
        DigestData d = new DigestData(1, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(), List.of(),
                List.of(),
                List.of(new DigestData.Mover("ETH", "Ethereum", new BigDecimal("-4.4"))),
                List.of(), List.of(),
                emptyMarket());

        String html = renderHtmlFor(d);

        assertThat(html).contains("Öne Çıkanlar");
        assertThat(html).contains("&#9660;");
        assertThat(html).doesNotContain("&#9650;");
    }

    // ── Market card: partial fields (some null, some present) ────────────────────

    @Test
    @DisplayName("Piyasa: yalnızca BIST100 + enflasyon dolu, diğerleri null → sadece dolu satırlar basılır")
    void marketPartialFields() {
        DigestData d = new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new DigestData.Market(null, new BigDecimal("9500"), null,
                        new BigDecimal("38.5"), null));

        String html = renderHtmlFor(d);

        assertThat(html).contains("Piyasa");
        assertThat(html).contains("BIST 100");
        assertThat(html).contains("Enflasyon");
        // null fields skipped
        assertThat(html).doesNotContain("Dolar/TL");
        assertThat(html).doesNotContain("Gram Altın");
        assertThat(html).doesNotContain("Politika Faizi");
    }

    // ── businessLog meta: frequency present → name() arm (already), id present arm ─

    @Test
    @DisplayName("Başarılı gönderim: businessLog publish + notification çağrılır (id/frequency non-null arm)")
    void successfulSendInvokesBusinessLogAndNotification() {
        NewsletterSubscription s = mkSub("u9", "u9@x.com", NewsletterFrequency.DAILY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("u9")).thenReturn(new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket()));

        service.sendDueDigests();

        verify(businessLog).publish(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
        verify(notificationService).createAndSend(anyString(), any(), anyString(),
                anyString(), any(), any(), any());
        assertThat(s.getLastSentAt()).isNotNull();
    }

    // ── WEEKLY due-arm (calendar-adaptive): exercises switch WEEKLY + frequencyLabel WEEKLY ─

    @Test
    @DisplayName("WEEKLY: bugün Pazartesi ise gönderilir (switch WEEKLY true + frequencyLabel haftalık); değilse atlanır")
    void weeklyDueDependsOnMonday() {
        LocalDate today = LocalDate.now(ZONE);
        NewsletterSubscription s = mkSub("uw", "uw@x.com", NewsletterFrequency.WEEKLY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("uw")).thenReturn(new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket()));

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        service.sendDueDigests();

        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            verify(emailSender).send(anyString(), anyString(), bodyCap.capture());
            // frequencyLabel WEEKLY arm appears in the footer text
            assertThat(bodyCap.getValue()).contains("haftalık");
        } else {
            verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }
    }

    // ── MONTHLY due-arm (calendar-adaptive): exercises switch MONTHLY + frequencyLabel MONTHLY ─

    @Test
    @DisplayName("MONTHLY: bugün ayın 1'i ise gönderilir (switch MONTHLY true + frequencyLabel aylık); değilse atlanır")
    void monthlyDueDependsOnFirstOfMonth() {
        LocalDate today = LocalDate.now(ZONE);
        NewsletterSubscription s = mkSub("um", "um@x.com", NewsletterFrequency.MONTHLY, null);
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("um")).thenReturn(new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket()));

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        service.sendDueDigests();

        if (today.getDayOfMonth() == 1) {
            verify(emailSender).send(anyString(), anyString(), bodyCap.capture());
            assertThat(bodyCap.getValue()).contains("aylık");
        } else {
            verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }
    }

    // ── isDue: lastSentAt set but on a DIFFERENT day → first guard false, proceeds ─

    @Test
    @DisplayName("isDue: lastSentAt geçmiş bir gün (bugün değil) → ilk koruma false, DAILY due olur")
    void lastSentPastDayStillDue() {
        NewsletterSubscription s = mkSub("up", "up@x.com", NewsletterFrequency.DAILY,
                LocalDateTime.now(ZONE).minusDays(3));
        when(repository.findByEnabledTrue()).thenReturn(List.of(s));
        when(digestPort.buildFor("up")).thenReturn(new DigestData(0, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                emptyMarket()));

        service.sendDueDigests();

        verify(emailSender).send(anyString(), anyString(), anyString());
    }
}
