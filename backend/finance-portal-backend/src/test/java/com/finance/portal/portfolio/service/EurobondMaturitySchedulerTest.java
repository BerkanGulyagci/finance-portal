package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EurobondMaturityScheduler} birim testleri. Tüm bağımlılıklar mock'lanır; cron metodu
 * doğrudan çağrılır (@Scheduled/@SchedulerLock Spring olmadan inert).
 */
@ExtendWith(MockitoExtension.class)
class EurobondMaturitySchedulerTest {

    /** BI maturityDate "M/d/yyyy" (örn. "8/17/2031"). */
    private static final DateTimeFormatter BI_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");

    @Mock PortfolioRepository portfolioRepository;
    @Mock EurobondService eurobondService;
    @Mock NotificationService notificationService;
    @Mock KeycloakUserAdminPort keycloakUserAdminPort;

    private EurobondMaturityScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EurobondMaturityScheduler(portfolioRepository, eurobondService,
                notificationService, keycloakUserAdminPort);
    }

    private static Portfolio portfolio(String userId, String name) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioTransaction buy(String symbol, BigDecimal qty, LocalDateTime when) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("90"));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }

    private static EurobondDetail detail(String isin, LocalDate maturity, String currency,
                                          BigDecimal fxRate, String couponPct,
                                          String paymentsPerYear, LocalDate finalCoupon) {
        EurobondDetail d = new EurobondDetail();
        d.setIsin(isin);
        d.setMaturityDate(maturity != null ? maturity.format(BI_FMT) : null);
        d.setCurrency(currency);
        d.setFxRate(fxRate);
        d.setCouponRate(couponPct);
        d.setPaymentsPerYear(paymentsPerYear);
        d.setFinalCouponDate(finalCoupon != null ? finalCoupon.format(BI_FMT) : null);
        return d;
    }

    private static AdminUserView verifiedUser(String id, String email) {
        return new AdminUserView(id, "u", email, "U", "1", true, true, List.of(), null, false, null, null);
    }

    @Test
    @DisplayName("Vadesi gelmiş eurobond SELL @ par×FX ile kapanır (US prefix)")
    void maturedEurobond_autoSellAtParTimesFx() {
        Portfolio p = portfolio("user-1", "My Bonds");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getAssetType()).isEqualTo(AssetType.BOND);
        // par(100) × fx(32) = 3200
        assertThat(sell.getPrice()).isEqualByComparingTo("3200");
        assertThat(sell.getQuantity()).isEqualByComparingTo("1000");
        assertThat(sell.getCommission()).isEqualByComparingTo("0");

        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq("u1@x.com"), eq(null));
    }

    @Test
    @DisplayName("Kuponlu eurobond için vade günü ek COUPON_INCOME yaratılır")
    void couponBearingEurobond_emitsFinalCoupon() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("XS1234567890", new BigDecimal("1000"), LocalDateTime.now().minusYears(3)));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // coupon 6%, paymentsPerYear 2, finalCouponDate = maturity → ödeme yapılır
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(detail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "6.000%", "2,0", maturity));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        // BUY + SELL + COUPON_INCOME = 3
        assertThat(p.getTransactions()).hasSize(3);
        PortfolioTransaction coupon = p.getTransactions().get(2);
        assertThat(coupon.getTransactionType()).isEqualTo(TransactionType.COUPON_INCOME);
        assertThat(coupon.getPrice()).isEqualByComparingTo("1");
        // native = 1000 × 6/100 / 2 = 30 ; × fx 32 = 960
        assertThat(coupon.getQuantity()).isEqualByComparingTo("960");
    }

    @Test
    @DisplayName("Önceki dönemde ödenmiş final kupon (finalCouponDate < maturity) atlanır")
    void finalCouponBeforeMaturity_skipped() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("XS1234567890", new BigDecimal("1000"), LocalDateTime.now().minusYears(3)));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(detail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "6.000%", "2,0", maturity.minusMonths(6)));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        // sadece BUY + SELL — kupon yok
        assertThat(p.getTransactions()).hasSize(2);
        assertThat(p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.COUPON_INCOME).count()).isZero();
    }

    @Test
    @DisplayName("FX null + TRY denomination → fx=1 ile par(100) fiyatı yazılır")
    void tryDenominated_fallsBackToFxOne() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US111AAA222", new BigDecimal("500"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US111AAA222"))
                .thenReturn(detail("US111AAA222", LocalDate.now(), "TRY", null, null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(2);
        assertThat(p.getTransactions().get(1).getPrice()).isEqualByComparingTo("100");
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("FX null + non-TRY → itfa atlanır (manuel kapatma)")
    void nonTryFxNull_skipped() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US111AAA222", new BigDecimal("500"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US111AAA222"))
                .thenReturn(detail("US111AAA222", LocalDate.now(), "USD", null, null, null, null));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(1); // sadece BUY
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Vadesi gelmemiş eurobond dokunulmaz")
    void notMaturedYet_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().plusYears(2), "USD",
                        new BigDecimal("32"), null, null, null));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Eurobond olmayan sembol (prefix yok, ISIN setinde yok) filtrelenir")
    void nonEurobondSymbol_ignored() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(eurobondService, never()).detail(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("ISIN setinde olan (prefix dışı) sembol eurobond sayılır")
    void symbolInIsinSet_treatedAsEurobond() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("JP9999BOND0", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of("JP9999BOND0"));
        when(eurobondService.detail("JP9999BOND0"))
                .thenReturn(detail("JP9999BOND0", LocalDate.now().minusDays(1), "JPY",
                        new BigDecimal("0.21"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(2);
        verify(eurobondService).detail("JP9999BOND0");
    }

    @Test
    @DisplayName("WATCHLIST portföyü hiç işlenmez")
    void watchlist_skipped() {
        Portfolio p = portfolio("user-1", "WL");
        p.setPortfolioType(PortfolioType.WATCHLIST);
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        lenient().when(eurobondService.currentIsins()).thenReturn(List.of());

        scheduler.processMaturedEurobonds();

        verify(eurobondService, never()).detail(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Zaten kapatılmış pozisyon (openQty=0) yeniden itfa edilmez")
    void alreadyClosed_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));
        PortfolioTransaction sell = new PortfolioTransaction();
        sell.setSymbol("US900123AL40");
        sell.setAssetType(AssetType.BOND);
        sell.setTransactionType(TransactionType.SELL);
        sell.setQuantity(new BigDecimal("1000"));
        sell.setPrice(new BigDecimal("3000"));
        sell.setCommission(BigDecimal.ZERO);
        sell.setTransactionDate(LocalDateTime.now().minusDays(3));
        p.addTransaction(sell);

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(2);
        verify(eurobondService, never()).detail(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Detail null/exception → holding atlanır, scheduler patlamaz")
    void detailUnavailable_gracefulSkip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40")).thenThrow(new RuntimeException("BI down"));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Geçersiz maturityDate string → parse null → atlanır")
    void unparseableMaturityDate_skipped() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(1)));

        EurobondDetail d = new EurobondDetail();
        d.setIsin("US900123AL40");
        d.setMaturityDate("not-a-date");
        d.setCurrency("USD");
        d.setFxRate(new BigDecimal("32"));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40")).thenReturn(d);

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("ISIN listesi servisi patlasa bile prefix bazlı tarama sürer")
    void isinServiceFailure_prefixFallbackStillWorks() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenThrow(new RuntimeException("ISIN list down"));
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(p.getTransactions()).hasSize(2); // prefix US ile yine yakalanır
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("Bildirim gönderimi patlarsa scheduler yine de tamamlanır (sessiz yakalama)")
    void notificationFailure_swallowed() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));
        when(notificationService.createAndSend(anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), any())).thenThrow(new RuntimeException("mail down"));

        scheduler.processMaturedEurobonds();

        // SELL yine de eklenmiş ve save edilmiş olmalı
        assertThat(p.getTransactions()).hasSize(2);
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("Bildirim body'sinde sembol + (varsa) kupon satırı yer alır")
    void notificationBody_containsSymbolAndCoupon() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("XS1234567890", new BigDecimal("1000"), LocalDateTime.now().minusYears(3)));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(detail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "6.000%", "2,0", maturity));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), html.capture(), anyString(), eq(null));
        assertThat(body.getValue()).contains("XS1234567890").contains("son kupon");
        assertThat(html.getValue()).contains("XS1234567890");
    }

    @Test
    @DisplayName("E-postası doğrulanmamış kullanıcıya email null geçilir ama bildirim yine gider")
    void unverifiedEmail_nullEmailButStillNotifies() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1"))
                .thenReturn(new AdminUserView("user-1", "u", "u1@x.com", "U", "1",
                        false /* emailVerified */, true, List.of(), null, false, null, null));

        scheduler.processMaturedEurobonds();

        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq(null) /* email */, eq(null));
    }

    @Test
    @DisplayName("Çok portföylü kullanıcıda email tek kez sorgulanır (cache)")
    void emailCachedAcrossPortfolios() {
        Portfolio p1 = portfolio("user-1", "P1");
        p1.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));
        Portfolio p2 = portfolio("user-1", "P2");
        p2.addTransaction(buy("XS1234567890", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p1, p2));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(detail("XS1234567890", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        verify(keycloakUserAdminPort, times(1)).getUser("user-1");
        verify(portfolioRepository, times(2)).save(any());
        verify(notificationService, times(2)).createAndSend(anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Boş portföy listesinde hiçbir şey yapılmaz")
    void noPortfolios_noop() {
        when(portfolioRepository.findAll()).thenReturn(List.of());

        scheduler.processMaturedEurobonds();

        verify(eurobondService, never()).detail(anyString());
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("COUPON_INCOME tx'leri açık-qty hesabına dahil edilmez")
    void couponIncomeTx_excludedFromOpenQty() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("US900123AL40", new BigDecimal("1000"), LocalDateTime.now().minusYears(2)));
        // var olan bir kupon nakit girişi — qty 50 TL, openQty'ye katılmamalı
        PortfolioTransaction coupon = new PortfolioTransaction();
        coupon.setSymbol("US900123AL40");
        coupon.setAssetType(AssetType.BOND);
        coupon.setTransactionType(TransactionType.COUPON_INCOME);
        coupon.setQuantity(new BigDecimal("50"));
        coupon.setPrice(BigDecimal.ONE);
        coupon.setCommission(BigDecimal.ZERO);
        coupon.setTransactionDate(LocalDateTime.now().minusMonths(6));
        p.addTransaction(coupon);

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(detail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        // openQty = 1000 (kupon dahil değil) → SELL.quantity 1000
        List<PortfolioTransaction> sells = p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.SELL)
                .collect(Collectors.toList());
        assertThat(sells).hasSize(1);
        assertThat(sells.get(0).getQuantity()).isEqualByComparingTo("1000");
    }
}
