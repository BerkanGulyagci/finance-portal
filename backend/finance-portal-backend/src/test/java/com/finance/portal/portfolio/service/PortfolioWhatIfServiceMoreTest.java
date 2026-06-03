package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfScenario;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesPoint;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ek kapsam: {@link PortfolioWhatIfService} — mevcut {@code PortfolioWhatIfServiceTest}'in
 * değinmediği yollar: gerçek fiyat serili gold/usd senaryoları, mevduat bileşik faktörü,
 * BOND kupon step-up (compute + series), FUTURE serisi yolu, spotTodayMv enjeksiyonu,
 * coupon-event step-up, kullanıcı benchmark'ları, computeSimSeries mutlu yol.
 */
class PortfolioWhatIfServiceMoreTest {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");

    private InflationDeflatorService deflator;
    private PortfolioHistoricalPricePort pricePort;
    private EconomyDataPort economyDataPort;
    private PortfolioCurrencyConverter currencyConverter;
    private PortfolioWhatIfService service;

    @BeforeEach
    void setUp() {
        deflator = mock(InflationDeflatorService.class);
        pricePort = mock(PortfolioHistoricalPricePort.class);
        economyDataPort = mock(EconomyDataPort.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);

        // default: empty price series for any symbol
        lenient().when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));
        // converter identity for TRY, ×30 for non-TRY
        lenient().when(currencyConverter.toTry(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> {
                    BigDecimal amount = inv.getArgument(0);
                    String curr = inv.getArgument(1);
                    if (amount == null) return null;
                    if (curr == null || "TRY".equalsIgnoreCase(curr) || "TL".equalsIgnoreCase(curr)) {
                        return amount;
                    }
                    return amount.multiply(new BigDecimal("30"));
                });
        lenient().when(currencyConverter.toTry(any(BigDecimal.class), eq((String) null)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new PortfolioWhatIfService(deflator, pricePort, economyDataPort, currencyConverter);
    }

    // ── compute(): gold scenario with real price series ──────────────────────

    @Test
    void compute_goldScenarioAvailable_usesPriceRatio() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1200"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // GOLD/GRAM series: at buyDate ≈ 2000, today (last entry) = 3000 → factor 1.5
        TreeMap<LocalDate, BigDecimal> goldSeries = new TreeMap<>();
        goldSeries.put(buyDate, new BigDecimal("2000"));
        goldSeries.put(LocalDate.now(TR), new BigDecimal("3000"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.GOLD), eq("GRAM"), any(), any()))
                .thenReturn(Optional.of(goldSeries));

        PortfolioWhatIfResult r = service.compute(resp);

        WhatIfScenario gold = scenario(r, "gold");
        assertThat(gold.isAvailable()).isTrue();
        // 1000 × (3000/2000) = 1500
        assertThat(gold.getValue()).isEqualByComparingTo("1500.00");
        // returnPct = (1500/1000 - 1)*100 = 50
        assertThat(gold.getReturnPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void compute_usdScenarioWithBeforeRangeDate_usesFirstEntry() {
        // buyDate is BEFORE the first series entry → floorEntry null → firstEntry used.
        LocalDate buyDate = LocalDate.of(2020, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        TreeMap<LocalDate, BigDecimal> usdSeries = new TreeMap<>();
        usdSeries.put(LocalDate.of(2025, 1, 1), new BigDecimal("10")); // first/earliest
        usdSeries.put(LocalDate.now(TR), new BigDecimal("40"));        // today/last → factor 4
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.of(usdSeries));

        PortfolioWhatIfResult r = service.compute(resp);

        WhatIfScenario usd = scenario(r, "usd");
        assertThat(usd.isAvailable()).isTrue();
        // todayPrice=40, base via firstEntry=10 → factor 4 → 1000×4 = 4000
        assertThat(usd.getValue()).isEqualByComparingTo("4000.00");
    }

    @Test
    void compute_priceScenarioTodayPriceZero_unavailable() {
        // Last entry value ≤ 0 → scenario unavailable.
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        TreeMap<LocalDate, BigDecimal> goldSeries = new TreeMap<>();
        goldSeries.put(buyDate, new BigDecimal("2000"));
        goldSeries.put(LocalDate.now(TR), BigDecimal.ZERO); // last entry zero
        when(pricePort.fetchDailyClosePrices(eq(AssetType.GOLD), eq("GRAM"), any(), any()))
                .thenReturn(Optional.of(goldSeries));

        PortfolioWhatIfResult r = service.compute(resp);

        WhatIfScenario gold = scenario(r, "gold");
        assertThat(gold.isAvailable()).isFalse();
        assertThat(gold.getNote()).isEqualTo("Veri yok");
    }

    @Test
    void compute_priceScenarioThrows_unavailable() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        when(pricePort.fetchDailyClosePrices(eq(AssetType.GOLD), eq("GRAM"), any(), any()))
                .thenThrow(new RuntimeException("price source down"));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(scenario(r, "gold").isAvailable()).isFalse();
    }

    // ── compute(): deposit scenario (compound) ───────────────────────────────

    @Test
    void compute_depositScenarioAvailable_compoundsRate() {
        LocalDate buyDate = LocalDate.now(TR).minusDays(365);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // single 50%/yr rate point dated before buyDate → ~1.5x over a year.
        long unix = buyDate.minusDays(30).atStartOfDay(TR).toEpochSecond();
        EconomySeriesPoint rate = new EconomySeriesPoint("rate", new BigDecimal("50"), unix);
        when(economyDataPort.fetchSeries(eq("TP.TRY.MT02"), any(), any()))
                .thenReturn(List.of(rate));

        PortfolioWhatIfResult r = service.compute(resp);

        WhatIfScenario dep = scenario(r, "deposit");
        assertThat(dep.isAvailable()).isTrue();
        // ~1000 × 1.5 over ~365 days; allow tolerance for day-count.
        assertThat(dep.getValue().doubleValue()).isBetween(1450.0, 1520.0);
    }

    @Test
    void compute_depositSeriesEmpty_unavailable() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));
        when(economyDataPort.fetchSeries(eq("TP.TRY.MT02"), any(), any())).thenReturn(List.of());

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(scenario(r, "deposit").isAvailable()).isFalse();
    }

    @Test
    void compute_depositSeriesThrows_unavailable() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));
        when(economyDataPort.fetchSeries(eq("TP.TRY.MT02"), any(), any()))
                .thenThrow(new RuntimeException("EVDS down"));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(scenario(r, "deposit").isAvailable()).isFalse();
    }

    // ── compute(): inflation unavailable branches ────────────────────────────

    @Test
    void compute_tufeSeriesThrows_inflationUnavailable() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));
        when(deflator.tufeSeries()).thenThrow(new RuntimeException("down"));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(scenario(r, "inflation").isAvailable()).isFalse();
    }

    @Test
    void compute_tufeNowIndexEmpty_inflationUnavailable() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));
        when(deflator.tufeSeries()).thenReturn(List.of(
                new EconomySeriesPoint("2026-01", new BigDecimal("100"), 0L)));
        // indexValueAt(today) empty → inflation unavailable
        when(deflator.indexValueAt(any(), any())).thenReturn(Optional.empty());

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(scenario(r, "inflation").isAvailable()).isFalse();
    }

    // ── compute(): BOND coupon step-up in actualValue ────────────────────────

    @Test
    void compute_bondWithCoupons_actualValueIncludesCoupons() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("TRT", AssetType.BOND,
                new BigDecimal("1000"), new BigDecimal("1050"), "TRY", buyDate.atStartOfDay());
        h.setSumCouponIncome(new BigDecimal("200"));
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        PortfolioWhatIfResult r = service.compute(resp);

        // actualValue = mvTl(1050) + coupons(200) = 1250
        assertThat(r.getActualValue()).isEqualByComparingTo("1250.00");
        // actualReturnPercent = (1250/1000 - 1)*100 = 25
        assertThat(r.getActualReturnPercent()).isEqualByComparingTo("25.00");
    }

    // ── compute(): lots with non-positive native cost → fallback to single Pos ─

    @Test
    void compute_lotsAllNonPositiveCost_fallsBackToSinglePos() {
        LocalDate buyDate = LocalDate.of(2025, 1, 1);
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1500"), "TRY", buyDate.atStartOfDay());
        // all lot costs ≤ 0 → totalLotCostNative ≤ 0 → single Pos fallback (included=1)
        h.setOpenCostLots(List.of(
                new PortfolioHoldingResponse.CostLot(buyDate, BigDecimal.ZERO),
                new PortfolioHoldingResponse.CostLot(buyDate, new BigDecimal("-5"))));
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(r.getIncludedHoldings()).isEqualTo(1);
        assertThat(r.getTotalCost()).isEqualByComparingTo("1000.00");
    }

    // ── computeSeries(): FUTURE synthetic actual line ────────────────────────

    @Test
    void computeSeries_future_syntheticActualFromNotional() {
        LocalDate buyDate = LocalDate.now(TR).minusMonths(4);
        PortfolioHoldingResponse h = holding("XU030F", AssetType.FUTURE,
                new BigDecimal("1000"), new BigDecimal("1600"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        WhatIfSeriesResult r = service.computeSeries(resp, "FUTURE", "XU030F", List.of());

        assertThat(r.getPoints()).isNotEmpty();
        assertThat(r.getAvailableScenarios()).contains("actual");
        // last point (today) → actual = futureNotionalMv = 1600
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getActual()).isEqualByComparingTo("1600.00");
        // first point (buyDate) → cost (margin) = 1000
        WhatIfSeriesPoint first = r.getPoints().get(0);
        assertThat(first.getActual()).isEqualByComparingTo("1000.00");
    }

    // ── computeSeries(): SPOT spotTodayMv snapshot at today ──────────────────

    @Test
    void computeSeries_spotNoSeries_emitsActualFromSpotTodayMv() {
        // No historical series → assetBase null, but spotTodayMv present → today point emits actual.
        LocalDate buyDate = LocalDate.now(TR).minusMonths(3);
        PortfolioHoldingResponse h = holding("FUNDX", AssetType.FUND,
                new BigDecimal("1000"), new BigDecimal("1234"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));
        // empty series for the fund (default mock returns empty TreeMap)

        WhatIfSeriesResult r = service.computeSeries(resp, "FUND", "FUNDX", List.of());

        assertThat(r.getPoints()).isNotEmpty();
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getActual()).isEqualByComparingTo("1234.00");
    }

    // ── computeSeries(): BOND coupon events step-up ──────────────────────────

    @Test
    void computeSeries_bondCouponEvents_addedToActual() {
        LocalDate buyDate = LocalDate.now(TR).minusMonths(6);
        PortfolioHoldingResponse h = holding("TRT", AssetType.BOND,
                new BigDecimal("9000"), new BigDecimal("10000"), "TRY", buyDate.atStartOfDay());
        h.setTotalQuantity(new BigDecimal("10000")); // parScale 100 → effQty 100
        // coupon event already paid (before today) → added to actual on/after its date
        LocalDate couponDate = buyDate.plusMonths(1);
        h.setCouponEvents(List.of(
                new PortfolioHoldingResponse.CouponEvent(couponDate, new BigDecimal("500"))));

        TreeMap<LocalDate, BigDecimal> bondSeries = new TreeMap<>();
        bondSeries.put(buyDate, new BigDecimal("90"));
        bondSeries.put(LocalDate.now(TR), new BigDecimal("100"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.BOND), eq("TRT"), any(), any()))
                .thenReturn(Optional.of(bondSeries));

        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        WhatIfSeriesResult r = service.computeSeries(resp, "BOND", "TRT", List.of());

        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        // today actual = spotTodayMv(10000) + coupon(500) = 10500
        assertThat(last.getActual()).isEqualByComparingTo("10500.00");
    }

    // ── computeSeries(): user benchmark (TL instrument) ──────────────────────

    @Test
    void computeSeries_benchmarkTl_appearsInExtraAndAvailable() {
        LocalDate buyDate = LocalDate.now(TR).minusMonths(3);
        PortfolioHoldingResponse h = holding("THYAO.IS", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // benchmark CRYPTO|ETH (TL, needsFx=false). Series doubles → factor 2.
        TreeMap<LocalDate, BigDecimal> ethSeries = new TreeMap<>();
        ethSeries.put(buyDate, new BigDecimal("100"));
        ethSeries.put(LocalDate.now(TR), new BigDecimal("200"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.CRYPTO), eq("ETH"), any(), any()))
                .thenReturn(Optional.of(ethSeries));

        WhatIfSeriesResult r = service.computeSeries(resp, "STOCK", "THYAO.IS",
                List.of("CRYPTO|ETH"));

        assertThat(r.getAvailableBenchmarks()).contains("CRYPTO|ETH");
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getExtra()).isNotNull();
        // 1000 × (200/100) = 2000
        assertThat(last.getExtra().get("CRYPTO|ETH")).isEqualByComparingTo("2000.00");
    }

    @Test
    void computeSeries_emptyPositions_returnsEmptyPoints() {
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of());

        WhatIfSeriesResult r = service.computeSeries(resp, null, null, List.of());

        assertThat(r.getPoints()).isEmpty();
        assertThat(r.getScope()).isEqualTo("ALL");
        assertThat(r.getLabel()).isEqualTo("Tüm Portföy");
    }

    // ── computeSimSeries(): happy path ───────────────────────────────────────

    @Test
    void computeSimSeries_validInput_buildsSyntheticPositionWithActual() {
        LocalDate buyDate = LocalDate.now(TR).minusMonths(4);

        // crypto BTC series (TL) so actual line is available
        TreeMap<LocalDate, BigDecimal> btcSeries = new TreeMap<>();
        btcSeries.put(buyDate, new BigDecimal("1000"));
        btcSeries.put(LocalDate.now(TR), new BigDecimal("1500"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.CRYPTO), eq("BTC"), any(), any()))
                .thenReturn(Optional.of(btcSeries));

        WhatIfSeriesResult r = service.computeSimSeries("CRYPTO", "BTC",
                new BigDecimal("5000"), buyDate, List.of());

        assertThat(r.getScope()).isEqualTo("SIM");
        assertThat(r.getLabel()).isEqualTo("BTC");
        assertThat(r.getPoints()).isNotEmpty();
        // amount=5000 invested; today spotTodayMv = amountTl(5000) since marketValue==amount
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getActual()).isEqualByComparingTo("5000.00");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static WhatIfScenario scenario(PortfolioWhatIfResult r, String key) {
        return r.getScenarios().stream()
                .filter(s -> key.equals(s.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scenario not found: " + key));
    }

    private static PortfolioHoldingResponse holding(String symbol, AssetType type,
                                                    BigDecimal cost, BigDecimal mv,
                                                    String currency, LocalDateTime firstBuyDate) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setAssetType(type);
        h.setTotalCost(cost);
        h.setMarketValue(mv);
        h.setCurrency(currency);
        h.setFirstBuyDate(firstBuyDate);
        return h;
    }
}
