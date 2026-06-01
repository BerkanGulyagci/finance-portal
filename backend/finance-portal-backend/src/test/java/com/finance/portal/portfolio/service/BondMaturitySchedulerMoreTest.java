package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
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
import java.util.List;
import java.util.UUID;

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
 * Ek kapsam: BondMaturitySchedulerTest'in dokunmadığı kategori-dispatch dalları —
 * par / EVDS son gösterge değeri (TÜFE) / gram-altın ödemeleri, son-kupon emisyonu,
 * Eurobond manuel-kapat bildirimi ve fiyat-çözülemez atlama yolları.
 */
@ExtendWith(MockitoExtension.class)
class BondMaturitySchedulerMoreTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock EvdsBondService evdsBondService;
    @Mock EurobondService eurobondService;
    @Mock GoldMarketService goldMarketService;
    @Mock NotificationService notificationService;
    @Mock KeycloakUserAdminPort keycloakUserAdminPort;

    private BondMaturityScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BondMaturityScheduler(portfolioRepository, evdsBondService,
                eurobondService, goldMarketService, notificationService, keycloakUserAdminPort);
    }

    private static Portfolio portfolio(String userId, String name) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioTransaction buy(String symbol, BigDecimal qty) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("90"));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDateTime.now().minusMonths(6));
        return t;
    }

    private static EvdsBondInstrument bond(String code, LocalDate maturity, BondCategory cat,
                                           BigDecimal indicator, BigDecimal couponRate) {
        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setInstrumentCode(code);
        b.setMaturityDate(maturity);
        b.setCategory(cat);
        b.setIndicatorValue(indicator);
        b.setCouponRate(couponRate);
        return b;
    }

    private static AdminUserView verifiedUser(String id) {
        return new AdminUserView(id, "u", id + "@x.com", "U", "1",
                true, true, List.of(), null, false, null, null);
    }

    private static GoldSpotResponse gold(BigDecimal official, BigDecimal gramClose) {
        GoldSpotResponse g = new GoldSpotResponse();
        g.setOfficialPureGoldGramTry(official);
        g.setGramCloseTry(gramClose);
        return g;
    }

    private PortfolioTransaction lastTx(Portfolio p) {
        return p.getTransactions().get(p.getTransactions().size() - 1);
    }

    // ── INDICATOR (TÜFE-endeksli) kategori — EVDS son gösterge değeri ile itfa ──

    @Test
    @DisplayName("TÜFE-endeksli bond son gösterge değeri ile kapanır")
    void inflationIndexed_sellsAtLastIndicator() {
        Portfolio p = portfolio("user-i", "Inflation");
        p.addTransaction(buy("TRT150927T11", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT150927T11"))
                .thenReturn(bond("TRT150927T11", LocalDate.now(),
                        BondCategory.INFLATION_INDEXED_BOND, new BigDecimal("173.45"), null));
        when(keycloakUserAdminPort.getUser("user-i")).thenReturn(verifiedUser("user-i"));

        scheduler.processMaturedBonds();

        PortfolioTransaction sell = lastTx(p);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getPrice()).isEqualByComparingTo("173.45");
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("TÜFE-endeksli bond gösterge değeri boşsa itfa atlanır (manuel kapatılmalı)")
    void inflationIndexed_nullIndicator_skipped() {
        Portfolio p = portfolio("user-i2", "Inflation2");
        p.addTransaction(buy("TRT150927K11", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT150927K11"))
                .thenReturn(bond("TRT150927K11", LocalDate.now(),
                        BondCategory.INFLATION_COUPON_STRIP, null, null));

        scheduler.processMaturedBonds();

        // Hiç SELL eklenmedi (sadece BUY), portföy save edilmedi, bildirim de yok
        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(),
                anyString(), anyString(), any(), any());
    }

    // ── GRAM-GOLD kategori — canlı gram altın TL fiyatı ile itfa ──

    @Test
    @DisplayName("Altın-endeksli bond canlı gram altın TL fiyatı ile kapanır")
    void goldIndexed_sellsAtGramGoldTry() {
        Portfolio p = portfolio("user-g", "Gold");
        p.addTransaction(buy("TRT000000G10", new BigDecimal("5")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(goldMarketService.getSpotGold())
                .thenReturn(gold(new BigDecimal("2750.50"), new BigDecimal("2700")));
        when(evdsBondService.getEvdsBondDetail("TRT000000G10"))
                .thenReturn(bond("TRT000000G10", LocalDate.now(),
                        BondCategory.GOLD_INDEXED_BOND, null, null));
        when(keycloakUserAdminPort.getUser("user-g")).thenReturn(verifiedUser("user-g"));

        scheduler.processMaturedBonds();

        PortfolioTransaction sell = lastTx(p);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        // officialPureGoldGramTry tercih edilir
        assertThat(sell.getPrice()).isEqualByComparingTo("2750.50");
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("Altın spotunda official null ise gramClose fallback kullanılır")
    void goldIndexed_officialNull_usesGramClose() {
        Portfolio p = portfolio("user-g2", "Gold2");
        p.addTransaction(buy("TRT000000G20", new BigDecimal("3")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(goldMarketService.getSpotGold()).thenReturn(gold(null, new BigDecimal("2680.00")));
        when(evdsBondService.getEvdsBondDetail("TRT000000G20"))
                .thenReturn(bond("TRT000000G20", LocalDate.now(),
                        BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE, null, null));
        when(keycloakUserAdminPort.getUser("user-g2")).thenReturn(verifiedUser("user-g2"));

        scheduler.processMaturedBonds();

        assertThat(lastTx(p).getPrice()).isEqualByComparingTo("2680.00");
    }

    @Test
    @DisplayName("Altın gram TL alınamazsa altın-endeksli itfa atlanır")
    void goldIndexed_noGoldPrice_skipped() {
        Portfolio p = portfolio("user-g3", "Gold3");
        p.addTransaction(buy("TRT000000G30", new BigDecimal("2")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(goldMarketService.getSpotGold()).thenThrow(new RuntimeException("BIST down"));
        when(evdsBondService.getEvdsBondDetail("TRT000000G30"))
                .thenReturn(bond("TRT000000G30", LocalDate.now(),
                        BondCategory.GOLD_INDEXED_BOND, null, null));

        scheduler.processMaturedBonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
    }

    // ── FINAL COUPON emisyonu — FIXED_COUPON_BOND vade gününde son kupon dilimi ──

    @Test
    @DisplayName("Sabit kuponlu bond vade gününde SELL + son COUPON_INCOME üretir")
    void fixedCoupon_emitsFinalCoupon() {
        Portfolio p = portfolio("user-c", "Coupon");
        p.addTransaction(buy("TRT121T2C10", new BigDecimal("10000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT121T2C10"))
                .thenReturn(bond("TRT121T2C10", LocalDate.now(),
                        BondCategory.FIXED_COUPON_BOND, new BigDecimal("98"), new BigDecimal("20")));
        when(keycloakUserAdminPort.getUser("user-c")).thenReturn(verifiedUser("user-c"));

        scheduler.processMaturedBonds();

        // BUY + SELL(par 100) + COUPON_INCOME = 3 tx
        assertThat(p.getTransactions()).hasSize(3);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getPrice()).isEqualByComparingTo("100"); // FIXED_COUPON_BOND par kategorisinde

        PortfolioTransaction coupon = p.getTransactions().get(2);
        assertThat(coupon.getTransactionType()).isEqualTo(TransactionType.COUPON_INCOME);
        assertThat(coupon.getPrice()).isEqualByComparingTo("1");
        // nominal 10000 × 20% / 2 ödeme = 1000 TL
        assertThat(coupon.getQuantity()).isEqualByComparingTo("1000");

        // E-posta gövdesinde son kupon satırı geçer
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-c"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), html.capture(), eq("user-c@x.com"), eq(null));
        assertThat(body.getValue()).contains("son kupon").contains("1000");
        assertThat(html.getValue()).contains("son kupon");
    }

    @Test
    @DisplayName("Kuponlu kategori ama couponRate yoksa son kupon üretilmez")
    void fixedCoupon_noRate_noCoupon() {
        Portfolio p = portfolio("user-c2", "Coupon2");
        p.addTransaction(buy("TRT121T2C20", new BigDecimal("10000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT121T2C20"))
                .thenReturn(bond("TRT121T2C20", LocalDate.now(),
                        BondCategory.TLREF_INDEXED_BOND, null, BigDecimal.ZERO));
        when(keycloakUserAdminPort.getUser("user-c2")).thenReturn(verifiedUser("user-c2"));

        scheduler.processMaturedBonds();

        // BUY + SELL = 2 tx; COUPON_INCOME yok (rate 0)
        assertThat(p.getTransactions()).hasSize(2);
        assertThat(p.getTransactions()).noneMatch(
                t -> t.getTransactionType() == TransactionType.COUPON_INCOME);
    }

    // ── UNKNOWN kategori → fallback par (100) ──

    @Test
    @DisplayName("Bilinmeyen/null kategori par 100 fallback ile kapanır")
    void unknownCategory_fallbackPar() {
        Portfolio p = portfolio("user-u", "Unknown");
        p.addTransaction(buy("TRT999999U10", new BigDecimal("4000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        // category null → MaturedHolding'de UNKNOWN'a düşer → fallback par
        when(evdsBondService.getEvdsBondDetail("TRT999999U10"))
                .thenReturn(bond("TRT999999U10", LocalDate.now(), null, null, null));
        when(keycloakUserAdminPort.getUser("user-u")).thenReturn(verifiedUser("user-u"));

        scheduler.processMaturedBonds();

        assertThat(lastTx(p).getPrice()).isEqualByComparingTo("100");
    }

    // ── Eurobond ISIN → manuel-kapat bildirimi (SELL yok) ──

    @Test
    @DisplayName("Eurobond ISIN'i SELL üretmez, manuel-kapat bildirimi gönderilir")
    void eurobondIsin_manualCloseNotification() {
        Portfolio p = portfolio("user-e", "Euro");
        p.addTransaction(buy("US900123AL40", new BigDecimal("10000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40", "XS123"));
        when(keycloakUserAdminPort.getUser("user-e")).thenReturn(verifiedUser("user-e"));

        scheduler.processMaturedBonds();

        // SELL eklenmedi, portföy save edilmedi (sadece bildirim)
        assertThat(p.getTransactions()).hasSize(1);
        verify(evdsBondService, never()).getEvdsBondDetail(anyString());
        verify(portfolioRepository, never()).save(any());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-e"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), html.capture(), eq("user-e@x.com"), eq(null));
        assertThat(body.getValue()).contains("US900123AL40").contains("manuel");
        assertThat(html.getValue()).contains("US900123AL40").contains("manuel");
    }

    // ── Eurobond ISIN listesi servis hatası → boş set, normal işleyiş ──

    @Test
    @DisplayName("Eurobond ISIN servisi patlarsa boş set ile devam edilir")
    void eurobondIsinService_failsGracefully() {
        Portfolio p = portfolio("user-x", "X");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenThrow(new RuntimeException("HMB down"));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-x")).thenReturn(verifiedUser("user-x"));

        scheduler.processMaturedBonds();

        assertThat(lastTx(p).getPrice()).isEqualByComparingTo("100");
        verify(portfolioRepository).save(p);
    }

    // ── E-posta: kullanıcı doğrulanmamış → email null geçilir ama bildirim yine gider ──

    @Test
    @DisplayName("E-posta doğrulanmamışsa bildirim email=null ile gönderilir")
    void unverifiedEmail_nullRecipient() {
        Portfolio p = portfolio("user-nv", "NoVerify");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-nv")).thenReturn(
                new AdminUserView("user-nv", "u", "u@x.com", "U", "1",
                        false, true, List.of(), null, false, null, null));

        scheduler.processMaturedBonds();

        verify(notificationService).createAndSend(eq("user-nv"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    @DisplayName("Keycloak hatası bildirim/itfayı bozmaz, email null geçer")
    void keycloakFailure_emailNull() {
        Portfolio p = portfolio("user-k", "K");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-k")).thenThrow(new RuntimeException("kc down"));

        scheduler.processMaturedBonds();

        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-k"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    @DisplayName("Bildirim gönderimi hata atsa bile scheduler patlamaz")
    void notificationThrows_swallowed() {
        Portfolio p = portfolio("user-n", "N");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-n")).thenReturn(verifiedUser("user-n"));
        when(notificationService.createAndSend(anyString(), any(), anyString(), anyString(),
                anyString(), any(), any())).thenThrow(new RuntimeException("smtp down"));

        // Patlamamalı — itfa SELL yine portföye yazılır
        scheduler.processMaturedBonds();

        verify(portfolioRepository).save(p);
        assertThat(p.getTransactions()).hasSize(2);
    }

    @Test
    @DisplayName("COUPON_INCOME işlemleri açık-nominal hesabına dahil edilmez")
    void couponIncomeTx_ignoredInOpenQty() {
        Portfolio p = portfolio("user-ci", "CI");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));
        // Daha önce kaydedilmiş bir kupon nakit girişi — qty=kupon tutarı, nominal değil
        PortfolioTransaction couponPast = new PortfolioTransaction();
        couponPast.setSymbol("TRB170626T13");
        couponPast.setAssetType(AssetType.BOND);
        couponPast.setTransactionType(TransactionType.COUPON_INCOME);
        couponPast.setQuantity(new BigDecimal("50"));
        couponPast.setPrice(BigDecimal.ONE);
        couponPast.setCommission(BigDecimal.ZERO);
        couponPast.setTransactionDate(LocalDateTime.now().minusMonths(1));
        p.addTransaction(couponPast);

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-ci")).thenReturn(verifiedUser("user-ci"));

        scheduler.processMaturedBonds();

        // SELL nominal = 1000 (kupon 50 hesaba katılmadı)
        PortfolioTransaction sell = lastTx(p);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getQuantity()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Açık pozisyon yoksa hiçbir servis çağrılmaz (boş portföyler)")
    void noOpenPositions_noWork() {
        Portfolio empty = portfolio("user-z", "Empty");

        when(portfolioRepository.findAll()).thenReturn(List.of(empty));
        lenient().when(eurobondService.currentIsins()).thenReturn(List.of());

        scheduler.processMaturedBonds();

        verify(evdsBondService, never()).getEvdsBondDetail(anyString());
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("FX-cinsli bond par (100) ile kapanır, kupon yok")
    void fxDenominated_par_noCoupon() {
        Portfolio p = portfolio("user-fx", "FX");
        p.addTransaction(buy("TRT000000F10", new BigDecimal("2000")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT000000F10"))
                .thenReturn(bond("TRT000000F10", LocalDate.now(),
                        BondCategory.FX_DENOMINATED_BOND, null, new BigDecimal("5")));
        when(keycloakUserAdminPort.getUser("user-fx")).thenReturn(verifiedUser("user-fx"));

        scheduler.processMaturedBonds();

        // FX_DENOMINATED_BOND par kategorisinde, FINAL_COUPON listesinde DEĞİL → kupon yok
        assertThat(p.getTransactions()).hasSize(2);
        assertThat(lastTx(p).getPrice()).isEqualByComparingTo("100");
        assertThat(p.getTransactions()).noneMatch(
                t -> t.getTransactionType() == TransactionType.COUPON_INCOME);
    }

    @Test
    @DisplayName("Otomatik kapanan ve manuel-kapat aynı portföyde tek bildirimde birleşir")
    void mixedAutoAndManual_singleNotification() {
        Portfolio p = portfolio("user-m", "Mixed");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("1000")));  // par DİBS
        p.addTransaction(buy("US900123AL40", new BigDecimal("2000")));  // eurobond manuel

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1),
                        BondCategory.ZERO_COUPON_BILL, null, null));
        when(keycloakUserAdminPort.getUser("user-m")).thenReturn(verifiedUser("user-m"));

        scheduler.processMaturedBonds();

        // 2 BUY + 1 SELL (par DİBS); eurobond için SELL yok
        assertThat(p.getTransactions()).hasSize(3);
        verify(portfolioRepository, times(1)).save(p);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(1)).createAndSend(eq("user-m"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), anyString(), anyString(), eq(null));
        assertThat(body.getValue()).contains("TRB170626T13").contains("US900123AL40");
    }
}
