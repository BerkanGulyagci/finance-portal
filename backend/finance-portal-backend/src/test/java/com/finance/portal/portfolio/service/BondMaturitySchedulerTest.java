package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.gold.GoldMarketService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondMaturitySchedulerTest {

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

    private static PortfolioTransaction buy(String symbol, BigDecimal qty, BigDecimal price, LocalDateTime when) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }

    private static EvdsBondInstrument bond(String code, LocalDate maturity) {
        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setInstrumentCode(code);
        b.setMaturityDate(maturity);
        return b;
    }

    private static EvdsBondInstrument bond(String code, LocalDate maturity, BondCategory cat,
                                           BigDecimal indicator, BigDecimal couponRate) {
        EvdsBondInstrument b = bond(code, maturity);
        b.setCategory(cat);
        b.setIndicatorValue(indicator);
        b.setCouponRate(couponRate);
        return b;
    }

    private static AdminUserView user(String id) {
        return new AdminUserView(id, "u", "u@x.com", "U", "1", true, true, List.of(), null, false, null, null);
    }

    private static PortfolioTransaction lastSell(Portfolio p) {
        return p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.SELL)
                .reduce((a, b) -> b).orElseThrow();
    }

    private static long couponTxCount(Portfolio p) {
        return p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.COUPON_INCOME).count();
    }

    @Test
    @DisplayName("Vadesi gelmiş BOND otomatik SELL @ par ile kapanır")
    void maturedBond_autoSellAtPar() {
        Portfolio p = portfolio("user-1", "My Portfolio");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("10000"),
                new BigDecimal("90"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now().minusDays(1))); // dün vade
        when(keycloakUserAdminPort.getUser("user-1"))
                .thenReturn(new AdminUserView("user-1", "u1", "u1@x.com", "U", "1",
                        true, true, List.of(), null, false, null, null));

        scheduler.processMaturedBonds();

        // Yeni SELL tx eklenmiş olmalı (toplam 2 tx: BUY + auto-SELL)
        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getPrice()).isEqualByComparingTo("100");
        assertThat(sell.getQuantity()).isEqualByComparingTo("10000");
        assertThat(sell.getCommission()).isEqualByComparingTo("0");

        verify(portfolioRepository).save(p);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq("u1@x.com"), eq(null));
    }

    @Test
    @DisplayName("Vadesi gelmemiş BOND dokunulmaz")
    void notMaturedYet_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRT240227T17", new BigDecimal("5000"),
                new BigDecimal("85"), LocalDateTime.now().minusMonths(3)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT240227T17"))
                .thenReturn(bond("TRT240227T17", LocalDate.now().plusYears(1))); // gelecekte

        scheduler.processMaturedBonds();

        assertThat(p.getTransactions()).hasSize(1); // sadece BUY
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Zaten kapatılmış pozisyon yeniden itfa edilmez")
    void alreadyClosed_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("10000"),
                new BigDecimal("90"), LocalDateTime.now().minusMonths(6)));
        PortfolioTransaction manualSell = new PortfolioTransaction();
        manualSell.setSymbol("TRB170626T13");
        manualSell.setAssetType(AssetType.BOND);
        manualSell.setTransactionType(TransactionType.SELL);
        manualSell.setQuantity(new BigDecimal("10000"));
        manualSell.setPrice(new BigDecimal("99"));
        manualSell.setCommission(BigDecimal.ZERO);
        manualSell.setTransactionDate(LocalDateTime.now().minusDays(5));
        p.addTransaction(manualSell);

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedBonds();

        // Açık pozisyon 0 — EVDS lookup hiç çağrılmaz, hiç SELL eklenmez
        assertThat(p.getTransactions()).hasSize(2);
        verify(evdsBondService, never()).getEvdsBondDetail(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Birden çok vadesi gelmiş holding tek bildirimle bildirilir")
    void multipleMatured_singleNotification() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRB170626T13", new BigDecimal("5000"),
                new BigDecimal("90"), LocalDateTime.now().minusMonths(6)));
        p.addTransaction(buy("TRT060127T10", new BigDecimal("3000"),
                new BigDecimal("80"), LocalDateTime.now().minusMonths(4)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRB170626T13"))
                .thenReturn(bond("TRB170626T13", LocalDate.now()));
        when(evdsBondService.getEvdsBondDetail("TRT060127T10"))
                .thenReturn(bond("TRT060127T10", LocalDate.now().minusDays(2)));
        when(keycloakUserAdminPort.getUser("user-1"))
                .thenReturn(new AdminUserView("user-1", "u1", "u1@x.com", "U", "1",
                        true, true, List.of(), null, false, null, null));

        scheduler.processMaturedBonds();

        // 2 BUY + 2 otomatik SELL = 4 tx
        assertThat(p.getTransactions()).hasSize(4);
        verify(portfolioRepository, times(1)).save(p);

        // Tek bildirim — body içinde her iki sembol de geçmeli
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(1)).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), bodyCaptor.capture(), anyString(), anyString(), eq(null));
        assertThat(bodyCaptor.getValue()).contains("TRB170626T13").contains("TRT060127T10");
    }

    @Test
    @DisplayName("WATCHLIST portföyü hiç işlenmez")
    void watchlistPortfolio_skipped() {
        Portfolio p = portfolio("user-1", "Watchlist");
        p.setPortfolioType(PortfolioType.WATCHLIST);
        p.addTransaction(buy("TRB170626T13", new BigDecimal("10000"),
                new BigDecimal("90"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedBonds();

        verify(evdsBondService, never()).getEvdsBondDetail(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("EVDS detail null dönerse holding atlanır, scheduler patlamaz")
    void evdsUnavailable_gracefulSkip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("UNKNOWN", new BigDecimal("10000"),
                new BigDecimal("90"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("UNKNOWN"))
                .thenThrow(new IllegalArgumentException("EVDS'de yok"));

        scheduler.processMaturedBonds();

        assertThat(p.getTransactions()).hasSize(1);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Döviz bondu itfada GÖSTERGE değerinden kapanır (par 100 değil), kupon yok")
    void fxBond_itfaAtIndicatorNotPar() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRT100528F15", new BigDecimal("1000"),
                new BigDecimal("40000"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT100528F15"))
                .thenReturn(bond("TRT100528F15", LocalDate.now().minusDays(1),
                        BondCategory.FX_DENOMINATED_BOND, new BigDecimal("46060.57"), new BigDecimal("2.6")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedBonds();

        assertThat(lastSell(p).getPrice()).isEqualByComparingTo("46060.57"); // gösterge (par 100 DEĞİL)
        assertThat(couponTxCount(p)).isZero();                                // FX → ayrı kupon yok
        assertThat(p.getTransactions()).hasSize(2);                           // BUY + SELL
    }

    @Test
    @DisplayName("TLREF kupon stripi (ISIN ...K..) itfada gösterge'den kapanır, kupon yok")
    void tlrefCouponStrip_itfaAtIndicatorNoCoupon() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRT211229K15", new BigDecimal("1000"),
                new BigDecimal("4"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT211229K15"))
                .thenReturn(bond("TRT211229K15", LocalDate.now().minusDays(1),
                        BondCategory.TLREF_INDEXED_BOND, new BigDecimal("5.282"), new BigDecimal("18.86")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedBonds();

        assertThat(lastSell(p).getPrice()).isEqualByComparingTo("5.282"); // gösterge (par 100 DEĞİL)
        assertThat(couponTxCount(p)).isZero();                            // strip → kupon yok
        assertThat(p.getTransactions()).hasSize(2);
    }

    @Test
    @DisplayName("TLREF ana para stripi (ISIN ...A..) itfada par 100'den kapanır, kupon yok")
    void tlrefPrincipalStrip_itfaAtParNoCoupon() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRT211229A13", new BigDecimal("1000"),
                new BigDecimal("80"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT211229A13"))
                .thenReturn(bond("TRT211229A13", LocalDate.now().minusDays(1),
                        BondCategory.TLREF_INDEXED_BOND, new BigDecimal("95"), new BigDecimal("18.86")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedBonds();

        assertThat(lastSell(p).getPrice()).isEqualByComparingTo("100"); // par (ana para stripi)
        assertThat(couponTxCount(p)).isZero();                          // strip → kupon yok
        assertThat(p.getTransactions()).hasSize(2);
    }

    @Test
    @DisplayName("TLREF tam tahvil (ISIN ...T..) itfada par 100 + son kupon ile kapanır")
    void tlrefFullBond_itfaAtParPlusCoupon() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRT211229T24", new BigDecimal("1000"),
                new BigDecimal("110"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRT211229T24"))
                .thenReturn(bond("TRT211229T24", LocalDate.now().minusDays(1),
                        BondCategory.TLREF_INDEXED_BOND, new BigDecimal("113.7"), new BigDecimal("18.86")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedBonds();

        assertThat(lastSell(p).getPrice()).isEqualByComparingTo("100"); // tam → par
        assertThat(couponTxCount(p)).isEqualTo(1);                      // son kupon eklenir
        assertThat(p.getTransactions()).hasSize(3);                     // BUY + SELL + COUPON_INCOME
    }

    @Test
    @DisplayName("Kira sertifikası kupon stripi (ISIN ...K..) itfada gösterge'den kapanır")
    void leaseCouponStrip_itfaAtIndicator() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(buy("TRD230233K17", new BigDecimal("1000"),
                new BigDecimal("0.5"), LocalDateTime.now().minusMonths(6)));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(evdsBondService.getEvdsBondDetail("TRD230233K17"))
                .thenReturn(bond("TRD230233K17", LocalDate.now().minusDays(1),
                        BondCategory.LEASE_CERTIFICATE, new BigDecimal("0.854"), new BigDecimal("5.71")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedBonds();

        assertThat(lastSell(p).getPrice()).isEqualByComparingTo("0.854"); // gösterge (par 100 DEĞİL)
        assertThat(couponTxCount(p)).isZero();
        assertThat(p.getTransactions()).hasSize(2);
    }
}
