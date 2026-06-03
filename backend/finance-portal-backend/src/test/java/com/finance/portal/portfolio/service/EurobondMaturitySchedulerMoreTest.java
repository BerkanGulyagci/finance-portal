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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EurobondMaturityScheduler} ek dal-kapsam testleri — mevcut
 * {@code EurobondMaturitySchedulerTest}'in kaçırdığı branch'leri (null/garbage parse,
 * currency null, paymentsPerYear=0 freq fallback, finalCouponDate null, sıfır kupon,
 * null isim escape, kullanıcı yok / hata e-posta, non-BOND & null-symbol tx filtresi,
 * currentIsins() null) hedefler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EurobondMaturitySchedulerMoreTest {

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

    // ── helpers (deliberately NOT named eq/any/etc. to avoid hiding Mockito matchers) ──

    private static Portfolio pos(String userId, String name) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioTransaction mkBuy(String symbol, BigDecimal qty) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("90"));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDateTime.now().minusYears(2));
        return t;
    }

    private static EurobondDetail mkDetail(String isin, LocalDate maturity, String currency,
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

    private static List<PortfolioTransaction> byType(Portfolio p, TransactionType type) {
        return p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == type)
                .collect(Collectors.toList());
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-BOND (STOCK) ve null-symbol BOND tx'leri açık-qty taramasında atlanır")
    void nonBondAndNullSymbolTransactions_skippedInScan() {
        Portfolio p = pos("user-1", "Mixed");

        // STOCK işlemi: assetType != BOND → line 131 atla
        PortfolioTransaction stock = new PortfolioTransaction();
        stock.setSymbol("ASELS");
        stock.setAssetType(AssetType.STOCK);
        stock.setTransactionType(TransactionType.BUY);
        stock.setQuantity(new BigDecimal("10"));
        stock.setPrice(new BigDecimal("50"));
        stock.setCommission(BigDecimal.ZERO);
        stock.setTransactionDate(LocalDateTime.now().minusYears(1));
        p.addTransaction(stock);

        // symbol == null BOND işlemi: line 134 atla
        PortfolioTransaction nullSym = new PortfolioTransaction();
        nullSym.setSymbol(null);
        nullSym.setAssetType(AssetType.BOND);
        nullSym.setTransactionType(TransactionType.BUY);
        nullSym.setQuantity(new BigDecimal("5"));
        nullSym.setPrice(new BigDecimal("90"));
        nullSym.setCommission(BigDecimal.ZERO);
        nullSym.setTransactionDate(LocalDateTime.now().minusYears(1));
        p.addTransaction(nullSym);

        // gerçek vadesi gelmiş eurobond
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        // STOCK ve null-symbol için detail çağrısı yok; sadece eurobond için var
        verify(eurobondService, never()).detail("ASELS");
        verify(eurobondService).detail("US900123AL40");
        // tek SELL yaratıldı (eurobond)
        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("BUY quantity null → ZERO sayılır; ikinci BUY ile openQty>0 kalır")
    void nullQuantityBuy_treatedAsZero() {
        Portfolio p = pos("user-1", "P");

        // qty null BUY → BigDecimal.ZERO (line 136 false arm)
        PortfolioTransaction nullQty = mkBuy("US900123AL40", null);
        p.addTransaction(nullQty);
        // gerçek qty taşıyan ikinci BUY → toplam openQty = 700
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("700")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("10"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        List<PortfolioTransaction> sells = byType(p, TransactionType.SELL);
        assertThat(sells).hasSize(1);
        // null qty 0 + 700 = 700
        assertThat(sells.get(0).getQuantity()).isEqualByComparingTo("700");
        // par 100 × fx 10 = 1000
        assertThat(sells.get(0).getPrice()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("currentIsins() null döndürürse boş set kullanılır; prefix taraması yine çalışır")
    void currentIsinsNull_emptySetUsed() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        // line 320: isins == null → Set.of()
        when(eurobondService.currentIsins()).thenReturn(null);
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("couponRate '%' (sayı yok) → parsePercent null → kupon yok")
    void couponRateEmptyAfterStrip_noCoupon() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("XS1234567890", new BigDecimal("1000")));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // couponRate "%" → cleaned "" → null (line 342)
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(mkDetail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "%", "2,0", maturity));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        assertThat(byType(p, TransactionType.COUPON_INCOME)).isEmpty();
    }

    @Test
    @DisplayName("couponRate sayısal değil ('abc') → NumberFormatException → kupon yok")
    void couponRateUnparseable_noCoupon() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("XS1234567890", new BigDecimal("1000")));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // couponRate "abc%" → NumberFormatException (line 344) → null
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(mkDetail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "abc%", "2,0", maturity));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        assertThat(byType(p, TransactionType.COUPON_INCOME)).isEmpty();
    }

    @Test
    @DisplayName("couponRate '0.000%' (signum<=0) → kupon hesaplanmaz")
    void zeroCouponRate_noCoupon() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("XS1234567890", new BigDecimal("1000")));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // couponPct parse 0 → computeFinalCouponTl line 224 signum()<=0 → null
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(mkDetail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "0.000%", "2,0", maturity));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        assertThat(byType(p, TransactionType.COUPON_INCOME)).isEmpty();
    }

    @Test
    @DisplayName("paymentsPerYear garbage → 0 → DEFAULT(2) frekansı; finalCouponDate null → kupon ödenir")
    void garbagePaymentsPerYearAndNullFinalCoupon_couponWithDefaultFreq() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("XS1234567890", new BigDecimal("1000")));

        LocalDate maturity = LocalDate.now().minusDays(1);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // paymentsPerYear "xx" → parse NumberFormatException → 0 (line 356)
        //   → computeFinalCouponTl freq = DEFAULT 2 (line 225 false arm)
        // finalCouponDate null → line 227 first operand false → kupon hesaplanır
        when(eurobondService.detail("XS1234567890"))
                .thenReturn(mkDetail("XS1234567890", maturity, "USD", new BigDecimal("32"),
                        "6.000%", "xx", null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        List<PortfolioTransaction> coupons = byType(p, TransactionType.COUPON_INCOME);
        assertThat(coupons).hasSize(1);
        // native = 1000 × 6/100 / 2(default) = 30 ; × fx 32 = 960
        assertThat(coupons.get(0).getQuantity()).isEqualByComparingTo("960");
    }

    @Test
    @DisplayName("currency null + fxRate var → SELL yazılır; bildirim gövdesinde currency boş basılır")
    void nullCurrencyWithFx_notificationOmitsCurrency() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // currency null (line 245/279 false arm), fxRate present → SELL @ par×fx
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), null,
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        List<PortfolioTransaction> sells = byType(p, TransactionType.SELL);
        assertThat(sells).hasSize(1);
        assertThat(sells.get(0).getPrice()).isEqualByComparingTo("3200");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), html.capture(), anyString(), eq(null));
        assertThat(body.getValue()).contains("US900123AL40");
        assertThat(html.getValue()).contains("US900123AL40");
    }

    @Test
    @DisplayName("Portföy ismi null → escape(null) '' döner, bildirim yine de hatasız gider")
    void nullPortfolioName_escapeHandlesNull() {
        Portfolio p = pos("user-1", null); // name null → escape(null) line 371
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        assertThat(byType(p, TransactionType.SELL)).hasSize(1);
        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), anyString(), eq(null));
    }

    @Test
    @DisplayName("Portföy ismi/sembolde HTML özel karakterleri escape edilir (&,<,>)")
    void htmlSpecialChars_escaped() {
        Portfolio p = pos("user-1", "A&B <Bonds>");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), html.capture(), anyString(), eq(null));
        // escape(&)->&amp;  escape(<)->&lt;  escape(>)->&gt;
        assertThat(html.getValue()).contains("A&amp;B &lt;Bonds&gt;");
    }

    @Test
    @DisplayName("getUser null döndürürse email null geçilir ama bildirim yine gönderilir")
    void userLookupReturnsNull_nullEmailStillNotifies() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        // line 363: u == null → email null
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(null);

        scheduler.processMaturedEurobonds();

        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    @DisplayName("getUser exception fırlatırsa email null geçilir, scheduler patlamaz")
    void userLookupThrows_nullEmailGraceful() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        // line 364: getUser throws → catch → null
        when(keycloakUserAdminPort.getUser("user-1")).thenThrow(new RuntimeException("keycloak down"));

        scheduler.processMaturedEurobonds();

        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    @DisplayName("Vadesi gelmemiş + vadesi gelmiş aynı portföyde: yalnız geleni itfa eder")
    void mixedMaturedAndNotMatured_onlyMaturedClosed() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000"))); // matured
        p.addTransaction(mkBuy("XS9999000011", new BigDecimal("500")));  // not matured

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(eurobondService.detail("XS9999000011"))
                .thenReturn(mkDetail("XS9999000011", LocalDate.now().plusYears(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        List<PortfolioTransaction> sells = byType(p, TransactionType.SELL);
        assertThat(sells).hasSize(1);
        assertThat(sells.get(0).getSymbol()).isEqualTo("US900123AL40");
        verify(portfolioRepository, times(1)).save(p);
    }

    @Test
    @DisplayName("Aynı taramada bir holding kapanır biri FX-null ile atlanır → portföy yine 1 kez save")
    void oneClosedOneSkippedByFx_savedOnce() {
        Portfolio p = pos("user-1", "P");
        p.addTransaction(mkBuy("US900123AL40", new BigDecimal("1000"))); // FX var → kapanır
        p.addTransaction(mkBuy("XS5555000022", new BigDecimal("400")));  // FX null + USD → atlanır

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(eurobondService.detail("US900123AL40"))
                .thenReturn(mkDetail("US900123AL40", LocalDate.now().minusDays(1), "USD",
                        new BigDecimal("32"), null, null, null));
        when(eurobondService.detail("XS5555000022"))
                .thenReturn(mkDetail("XS5555000022", LocalDate.now().minusDays(1), "USD",
                        null, null, null, null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1", "u1@x.com"));

        scheduler.processMaturedEurobonds();

        // sadece bir SELL (FX-null olan atlandı ama closed boş değil → save yine olur)
        List<PortfolioTransaction> sells = byType(p, TransactionType.SELL);
        assertThat(sells).hasSize(1);
        assertThat(sells.get(0).getSymbol()).isEqualTo("US900123AL40");
        verify(portfolioRepository, times(1)).save(p);
    }
}
