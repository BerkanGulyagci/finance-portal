package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.performance.PortfolioPerformanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PortfolioPerformanceServiceTest {

    private PortfolioPersistencePort persistence;
    private PortfolioHistoricalPricePort pricePort;
    private PortfolioPerformanceCalculator calculator;
    private PortfolioHoldingsBuilder holdingsBuilder;
    private PortfolioCurrencyConverter currencyConverter;
    private InflationDeflatorService deflator;
    private PortfolioPerformanceService service;

    private static final String USER_ID = "u1";
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        persistence = mock(PortfolioPersistencePort.class);
        pricePort = mock(PortfolioHistoricalPricePort.class);
        calculator = mock(PortfolioPerformanceCalculator.class);
        holdingsBuilder = mock(PortfolioHoldingsBuilder.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);
        deflator = mock(InflationDeflatorService.class);

        // currencyConverter — TRY için identity
        when(currencyConverter.toTry(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        // holdingsBuilder default: boş holdings → align step erken çıkar
        when(holdingsBuilder.build(any())).thenReturn(List.of());
        // deflator default: TÜFE yok → baseline atlanır
        when(deflator.tufeSeries()).thenReturn(List.of());
        // calculator default: boş listede points yok
        when(calculator.calculate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new java.util.ArrayList<>());

        service = new PortfolioPerformanceService(persistence, pricePort, calculator,
                holdingsBuilder, currencyConverter, deflator);
    }

    // ------------------------------ validation ------------------------------

    @Test
    @DisplayName("getPerformance: portföy bulunamaz → IllegalArgumentException")
    void getPerformance_portfolioNotFound_throws() {
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Portfolio not found");
    }

    @Test
    @DisplayName("getPerformance: WATCHLIST portföy → IllegalArgumentException")
    void getPerformance_watchlist_rejected() {
        Portfolio wl = new Portfolio();
        wl.setId(PORTFOLIO_ID);
        wl.setUserId(USER_ID);
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(wl));

        assertThatThrownBy(() -> service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only available for holdings");
    }

    // ------------------------------ happy path ------------------------------

    @Test
    @DisplayName("getPerformance: HOLDINGS, transaction yok → boş points, currency TRY")
    void getPerformance_noTransactions_emptyResult() {
        Portfolio p = holdingsPortfolio(List.of());
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        assertThat(r.getPortfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getRange()).isEqualTo("1Y");
        assertThat(r.getMetric()).isEqualTo("VALUE");
        assertThat(r.getPoints()).isEmpty();
        assertThat(r.getExcludedAssets()).isEmpty();
    }

    @Test
    @DisplayName("getPerformance: transaction var ama tarihsel veri yok → asset 'excluded' listesinde")
    void getPerformance_noHistoricalData_assetExcluded() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        assertThat(r.getExcludedAssets()).hasSize(1);
        assertThat(r.getExcludedAssets().get(0).getAssetType()).isEqualTo(AssetType.STOCK);
    }

    @Test
    @DisplayName("getPerformance: tarihsel veri var → calculator çağrılır, priceSeries iletilir")
    void getPerformance_priceSeriesPresent_calculatorReceivesIt() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.now().minusDays(30), new BigDecimal("100"));
        series.put(LocalDate.now(), new BigDecimal("120"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        verify(calculator).calculate(eq(p.getTransactions()),
                any(LocalDate.class), any(LocalDate.class),
                any(java.util.Map.class), any(java.util.Set.class), any(java.util.List.class));
        // Excluded boş (priceSeries var)
        verify(pricePort).fetchDailyClosePrices(eq(AssetType.STOCK), eq("THYAO"),
                any(LocalDate.class), any(LocalDate.class));
    }

    // ------------------------------ trimLeadingZeroValuePoints ------------------------------

    @Test
    @DisplayName("getPerformance: serinin başındaki sıfır-değerli günler atılır")
    void getPerformance_trimsLeadingZeros() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));

        // Calculator: ilk 2 nokta 0 değerli, sonraki 3 dolu
        List<PortfolioPerformancePoint> stubbed = new java.util.ArrayList<>(List.of(
                point(LocalDate.of(2026, 1, 1), "0"),
                point(LocalDate.of(2026, 1, 2), "0"),
                point(LocalDate.of(2026, 1, 3), "100"),
                point(LocalDate.of(2026, 1, 4), "120"),
                point(LocalDate.of(2026, 1, 5), "130")));
        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(stubbed);

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        // Baştaki 2 sıfır atılmalı
        assertThat(r.getPoints()).hasSize(3);
        assertThat(r.getPoints().get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 3));
    }

    @Test
    @DisplayName("getPerformance: hepsi sıfır-değerli → boş points listesi döner")
    void getPerformance_allZeroValued_emptyPoints() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));

        List<PortfolioPerformancePoint> stubbed = new java.util.ArrayList<>(List.of(
                point(LocalDate.of(2026, 1, 1), "0"),
                point(LocalDate.of(2026, 1, 2), null)));
        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(stubbed);

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        assertThat(r.getPoints()).isEmpty();
    }

    // ------------------------------ alignLastPointWithLiveHoldings ------------------------------

    @Test
    @DisplayName("getPerformance: bugünkü son nokta live holding'lerle hizalanır")
    void getPerformance_lastPointAlignedWithLiveHoldings() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        List<PortfolioPerformancePoint> stubbed = new java.util.ArrayList<>(List.of(
                point(today.minusDays(1), "1000"),
                point(today, "1200")));
        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(stubbed);

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("THYAO");
        h.setMarketValue(new BigDecimal("1500"));
        h.setTotalCost(new BigDecimal("1000"));
        h.setCurrency("TRY");
        when(holdingsBuilder.build(any())).thenReturn(List.of(h));

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        // Son nokta canlı 1500/1000 ile güncellenmiş
        PortfolioPerformancePoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getMarketValue()).isEqualByComparingTo("1500");
        assertThat(last.getTotalCost()).isEqualByComparingTo("1000");
        assertThat(last.getProfitLoss()).isEqualByComparingTo("500");
        assertThat(last.getProfitLossPercent()).isEqualByComparingTo("50.00");
    }

    // ------------------------------ inflation baseline ------------------------------

    @Test
    @DisplayName("getPerformance: TÜFE serisi varsa enflasyon baseline her noktaya yazılır")
    void getPerformance_inflationBaselineApplied() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));
        List<PortfolioPerformancePoint> stubbed = new java.util.ArrayList<>(List.of(
                point(LocalDate.of(2026, 1, 1), "1000"),
                point(LocalDate.of(2026, 2, 1), "1100")));
        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(stubbed);

        // TÜFE: Ocak=100, Şubat=110 → 10% yıllık enflasyon, baseline 1000 → 1100
        when(deflator.tufeSeries()).thenReturn(List.of(
                new EconomySeriesPoint("2026-01-01", new BigDecimal("100"), 0L)));
        when(deflator.indexValueAt(any(), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(Optional.of(new BigDecimal("100")));
        when(deflator.indexValueAt(any(), eq(LocalDate.of(2026, 2, 1))))
                .thenReturn(Optional.of(new BigDecimal("110")));

        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        // İlk nokta: 1000 × (100/100) = 1000
        assertThat(r.getPoints().get(0).getInflationValue()).isEqualByComparingTo("1000");
        // İkinci nokta: 1000 × (110/100) = 1100
        assertThat(r.getPoints().get(1).getInflationValue()).isEqualByComparingTo("1100");
    }

    @Test
    @DisplayName("getPerformance: TÜFE service exception fırlatırsa baseline atlanır (graceful)")
    void getPerformance_inflationServiceThrows_skipped() {
        PortfolioTransaction tx = buyTx("THYAO", AssetType.STOCK, "10", "100",
                LocalDate.now().minusMonths(2).atStartOfDay());
        Portfolio p = holdingsPortfolio(List.of(tx));
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));
        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(
                new java.util.ArrayList<>(List.of(point(LocalDate.of(2026, 1, 1), "1000"))));
        when(deflator.tufeSeries()).thenThrow(new RuntimeException("EVDS down"));

        // Hata fırlatmamalı, sadece inflation alanı null kalır
        PortfolioPerformanceResult r = service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "VALUE");

        assertThat(r.getPoints()).hasSize(1);
        assertThat(r.getPoints().get(0).getInflationValue()).isNull();
    }

    // ------------------------------ range parsing ------------------------------

    @Test
    @DisplayName("getPerformance: farklı range parametreleri (1M, 6M, YTD, 1Y) sonucu doğru etiketler")
    void getPerformance_rangeLabels() {
        Portfolio p = holdingsPortfolio(List.of());
        when(persistence.findByIdAndUserId(PORTFOLIO_ID, USER_ID)).thenReturn(Optional.of(p));

        assertThat(service.getPerformance(USER_ID, PORTFOLIO_ID, "1M", "VALUE").getRange()).isEqualTo("1M");
        assertThat(service.getPerformance(USER_ID, PORTFOLIO_ID, "6M", "VALUE").getRange()).isEqualTo("6M");
        assertThat(service.getPerformance(USER_ID, PORTFOLIO_ID, "YTD", "VALUE").getRange()).isEqualTo("YTD");
        assertThat(service.getPerformance(USER_ID, PORTFOLIO_ID, "1Y", "GROWTH").getMetric()).isEqualTo("GROWTH");
    }

    // ------------------------------ helpers ------------------------------

    private static Portfolio holdingsPortfolio(List<PortfolioTransaction> txs) {
        Portfolio p = new Portfolio();
        p.setId(PORTFOLIO_ID);
        p.setUserId(USER_ID);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setTransactions(txs);
        return p;
    }

    private static PortfolioTransaction buyTx(String symbol, AssetType type,
                                              String qty, String price, LocalDateTime date) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(type);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(date);
        return t;
    }

    private static PortfolioPerformancePoint point(LocalDate date, String marketValue) {
        PortfolioPerformancePoint p = new PortfolioPerformancePoint();
        p.setDate(date);
        if (marketValue != null) {
            p.setMarketValue(new BigDecimal(marketValue));
            p.setTotalCost(new BigDecimal(marketValue));
        }
        return p;
    }
}
