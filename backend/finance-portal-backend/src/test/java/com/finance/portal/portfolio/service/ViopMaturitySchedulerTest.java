package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ViopMaturityScheduler} birim testleri. Asıl odak: vadesi dolan VİOP pozisyonunun
 * <b>kendi yönünü (LONG/SHORT) taşıyan</b> bir SELL ile settlement fiyatından otomatik kapanması.
 * Kontrat adları gerçek formatta ({@code "USDTRY (30 OCA 24) VADELI"}) çünkü
 * {@code ViopService.parseContractMaturity} statiktir ve gerçekten parse edilir.
 */
@ExtendWith(MockitoExtension.class)
class ViopMaturitySchedulerTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock ViopService viopService;
    @Mock ViopChartService viopChartService;
    @Mock NotificationService notificationService;
    @Mock KeycloakUserAdminPort keycloakUserAdminPort;

    private ViopMaturityScheduler scheduler;

    // Geçmiş vadeler (bugünden kesinlikle önce) — otomatik kapanmalı:
    private static final String EXPIRED_LONG  = "USDTRY (30 OCA 24) VADELI";  // 2024-01-30
    private static final String EXPIRED_SHORT = "EURUSD (28 SUB 24) VADELI";  // 2024-02-28
    // Gelecek vade (kesinlikle sonra) — dokunulmamalı:
    private static final String FUTURE_LONG   = "USDTRY (30 ARA 30) VADELI";  // 2030-12-30

    @BeforeEach
    void setUp() {
        scheduler = new ViopMaturityScheduler(portfolioRepository, viopService,
                viopChartService, notificationService, keycloakUserAdminPort);
    }

    private static Portfolio portfolio(String userId, String name) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioTransaction futureTx(String symbol, String direction, TransactionType type,
                                                 BigDecimal qty, BigDecimal price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(AssetType.FUTURE);
        t.setTransactionType(type);
        t.setDirection(direction);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDateTime.now().minusMonths(6));
        return t;
    }

    private static ViopContractDetail detailWithSettlement(BigDecimal settle) {
        ViopContractDetail d = new ViopContractDetail();
        d.setSettlementPrice(settle);
        return d;
    }

    private static AdminUserView user(String id) {
        return new AdminUserView(id, "u", "u@x.com", "U", "1", true, true, List.of(), null, false, null, null);
    }

    @Test
    @DisplayName("LONG vadesi dolunca SELL(LONG) @ settlement ile otomatik kapanır")
    void expiredLong_autoClosesWithLongSell() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detailWithSettlement(new BigDecimal("120")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getAssetType()).isEqualTo(AssetType.FUTURE);
        assertThat(sell.getDirection()).isEqualTo("LONG");
        assertThat(sell.getQuantity()).isEqualByComparingTo("10");
        assertThat(sell.getPrice()).isEqualByComparingTo("120");
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("SHORT vadesi dolunca SELL(SHORT) @ settlement ile kapanır (yön korunur)")
    void expiredShort_autoClosesWithShortSell() {
        Portfolio p = portfolio("user-1", "P");
        // SHORT açıldı (entry 100), fiyat düştü (settlement 70) → realized = (70−100)×qty×mult×(−1) = kâr
        p.addTransaction(futureTx(EXPIRED_SHORT, "SHORT", TransactionType.BUY, new BigDecimal("5"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(viopService.getContractDetailCached(EXPIRED_SHORT)).thenReturn(detailWithSettlement(new BigDecimal("70")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getDirection()).isEqualTo("SHORT"); // ← yön kapanış işlemine taşındı
        assertThat(sell.getQuantity()).isEqualByComparingTo("5");
        assertThat(sell.getPrice()).isEqualByComparingTo("70");
        verify(portfolioRepository).save(p);
    }

    @Test
    @DisplayName("Vadesi gelmemiş VİOP dokunulmaz (detay bile sorulmaz)")
    void notExpired_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureTx(FUTURE_LONG, "LONG", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(1);
        verify(viopService, never()).getContractDetailCached(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Zaten kapatılmış pozisyon (openQty 0) yeniden kapatılmaz")
    void alreadyClosed_skip() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100")));
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG", TransactionType.SELL, new BigDecimal("10"), new BigDecimal("110")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2); // yeni tx yok
        verify(viopService, never()).getContractDetailCached(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("LONG ve SHORT aynı kontratta ayrı havuzlar — ikisi de kendi yönüyle kapanır")
    void longAndShortSameContract_closeIndependently() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG",  TransactionType.BUY, new BigDecimal("3"), new BigDecimal("100")));
        p.addTransaction(futureTx(EXPIRED_LONG, "SHORT", TransactionType.BUY, new BigDecimal("7"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detailWithSettlement(new BigDecimal("90")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(4); // 2 BUY + 2 SELL
        List<PortfolioTransaction> sells = p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.SELL).toList();
        assertThat(sells).hasSize(2);
        assertThat(sells).anySatisfy(s -> {
            assertThat(s.getDirection()).isEqualTo("LONG");
            assertThat(s.getQuantity()).isEqualByComparingTo("3");
        });
        assertThat(sells).anySatisfy(s -> {
            assertThat(s.getDirection()).isEqualTo("SHORT");
            assertThat(s.getQuantity()).isEqualByComparingTo("7");
        });
    }

    @Test
    @DisplayName("Settlement bulunamazsa kapatılmaz → manuel kapatma bildirimi")
    void noSettlement_manualNotification() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(null);
        when(viopChartService.getChart(eq(EXPIRED_LONG), any())).thenReturn(List.of());
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1"));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(1);            // kapanış yok
        verify(portfolioRepository, never()).save(any());     // değişiklik yok
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), body.capture(), anyString(), anyString(), eq(null));
        assertThat(body.getValue()).contains(EXPIRED_LONG).contains("manuel");
    }

    @Test
    @DisplayName("WATCHLIST portföyü hiç işlenmez")
    void watchlist_skipped() {
        Portfolio p = portfolio("user-1", "W");
        p.setPortfolioType(PortfolioType.WATCHLIST);
        p.addTransaction(futureTx(EXPIRED_LONG, "LONG", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        verify(viopService, never()).getContractDetailCached(anyString());
        verify(portfolioRepository, never()).save(any());
    }
}
