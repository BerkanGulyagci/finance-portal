package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
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
 * Üçüncü tur ek kapsam: {@link PortfolioWhatIfService} — ilk iki test sınıfının değinmediği
 * computeSeries iç-toplama dalları. Asıl hedef: TÜM paylaşılan piyasa/ekonomi serileri
 * (altın/usd/bist/btc/gspc/tüfe/us-cpi/mevduat) AYNI ANDA dolu olduğunda çalışan accumulation
 * gövdesi (gold/usd/inflation/usInflation/deposit/bist/sp500 + needsFx benchmark FX yolu),
 * trimLeadingLowCapital kuyruk-kesme, floorVal firstEntry fallback, depositFactor iç bileşik
 * döngüsü ve parseBenchmarks needsFx (STOCK !.IS / FUTURE =F) dalları.
 */
class PortfolioWhatIfServiceMoreTest2 {

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

        // default: empty price series for any symbol (overridden per-symbol below)
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

    // ──────────────────────────────────────────────────────────────────────────
    // FLAGSHIP: computeSeries with EVERY shared series populated → exercises the
    // whole per-position accumulation body (gold/usd/inflation/usInflation/
    // deposit/bist/sp500) + a needsFx benchmark (FX-converted extra) + all the
    // setX(anyX?...) emit branches + every avail.add(...) line.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void computeSeries_allBenchmarksAvailable_accumulatesEveryScenario() {
        LocalDate today = LocalDate.now(TR);
        LocalDate buyDate = today.minusMonths(6);

        // Single STOCK position, TRY. Use single-mode filter so trimLeadingLowCapital is skipped
        // and the position deterministically maps to its own series.
        PortfolioHoldingResponse h = holding("THYAO.IS", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1500"), "TRY", buyDate.atStartOfDay());
        h.setTotalQuantity(new BigDecimal("10"));   // effQty path for "actual" (qty × price)
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // Per-holding asset series (the STOCK itself): base 100 → today 150.
        twoPointSeries(AssetType.STOCK, "THYAO.IS", buyDate, "100", today, "150");

        // All shared market series populated → every *Avail flag true.
        twoPointSeries(AssetType.GOLD, "GRAM", buyDate, "2000", today, "3000");   // gold ×1.5
        twoPointSeries(AssetType.FX, "USD", buyDate, "20", today, "40");          // usd ×2
        twoPointSeries(AssetType.STOCK, "XU100.IS", buyDate, "5000", today, "10000"); // bist ×2
        twoPointSeries(AssetType.CRYPTO, "BTC", buyDate, "1000000", today, "2000000"); // btc ×2
        twoPointSeries(AssetType.STOCK, "^GSPC", buyDate, "4000", today, "5000");  // gspc ×1.25

        // needsFx benchmark: STOCK|AAPL (not .IS) → needsFx=true → FX-converted extra path.
        twoPointSeries(AssetType.STOCK, "AAPL", buyDate, "100", today, "200");     // bench ×2

        // Inflation (TÜFE) — series non-empty + cumulativeFactor returns a positive factor.
        EconomySeriesPoint tufePt = new EconomySeriesPoint("2025-1", new BigDecimal("100"), 0L);
        when(deflator.tufeSeries()).thenReturn(List.of(tufePt));
        when(deflator.cumulativeFactor(any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("1.30")));

        // US CPI — series non-empty + indexValueAt returns positive (used for base & each t).
        EconomySeriesPoint cpiPt = new EconomySeriesPoint("2025-1", new BigDecimal("300"), 0L);
        when(deflator.usCpiSeries()).thenReturn(List.of(cpiPt));
        when(deflator.indexValueAt(any(), any())).thenReturn(Optional.of(new BigDecimal("310")));

        // Deposit rate series (EVDS) — single 40%/yr point before buyDate → compounds.
        long unix = buyDate.minusDays(20).atStartOfDay(TR).toEpochSecond();
        EconomySeriesPoint rate = new EconomySeriesPoint("rate", new BigDecimal("40"), unix);
        when(economyDataPort.fetchSeries(eq("TP.TRY.MT02"), any(), any()))
                .thenReturn(List.of(rate));

        WhatIfSeriesResult r = service.computeSeries(resp, "STOCK", "THYAO.IS",
                List.of("STOCK|AAPL"));

        // every scenario reported as available
        assertThat(r.getAvailableScenarios())
                .contains("actual", "inflation", "usInflation", "gold", "usd",
                        "deposit", "bist100", "bitcoin", "sp500");
        assertThat(r.getAvailableBenchmarks()).contains("STOCK|AAPL");

        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        // actual = today spotTodayMv snapshot = marketValue 1500 (today point uses snapshot)
        assertThat(last.getActual()).isEqualByComparingTo("1500.00");
        // gold = 1000 × (3000/2000) = 1500
        assertThat(last.getGold()).isEqualByComparingTo("1500.00");
        // usd = 1000 × (40/20) = 2000
        assertThat(last.getUsd()).isEqualByComparingTo("2000.00");
        // bist = 1000 × (10000/5000) = 2000
        assertThat(last.getBist100()).isEqualByComparingTo("2000.00");
        // bitcoin = 1000 × (2000000/1000000) = 2000
        assertThat(last.getBitcoin()).isEqualByComparingTo("2000.00");
        // inflation = 1000 × 1.30 = 1300
        assertThat(last.getInflation()).isEqualByComparingTo("1300.00");
        // sp500 = 1000 × (5000/4000) × (40/20) = 1000 × 1.25 × 2 = 2500
        assertThat(last.getSp500()).isEqualByComparingTo("2500.00");
        // usInflation = 1000 × (310/310) × (40/20) = 1000 × 1 × 2 = 2000
        assertThat(last.getUsInflation()).isEqualByComparingTo("2000.00");
        // bench AAPL needsFx = 1000 × (200/100) × (40/20) = 4000
        assertThat(last.getExtra()).isNotNull();
        assertThat(last.getExtra().get("STOCK|AAPL")).isEqualByComparingTo("4000.00");
        // deposit grew above principal
        assertThat(last.getDeposit().doubleValue()).isGreaterThan(1000.0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // trimLeadingLowCapital: ALL-portfolio (single=false) with an early tiny
    // position then a much larger later one → leading low-capital points trimmed.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void computeSeries_allPortfolio_trimsLeadingLowCapitalTail() {
        LocalDate today = LocalDate.now(TR);
        LocalDate oldDate = today.minusMonths(10); // tiny early position
        LocalDate newDate = today.minusMonths(2);  // large later position

        PortfolioHoldingResponse tiny = holding("AAA", AssetType.STOCK,
                new BigDecimal("10"), new BigDecimal("10"), "TRY", oldDate.atStartOfDay());
        PortfolioHoldingResponse big = holding("BBB", AssetType.STOCK,
                new BigDecimal("10000"), new BigDecimal("10000"), "TRY", newDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(tiny, big));

        // ALL-portfolio (no filter) → single=false → trimLeadingLowCapital runs.
        WhatIfSeriesResult r = service.computeSeries(resp, null, null, List.of());

        assertThat(r.getScope()).isEqualTo("ALL");
        assertThat(r.getPoints()).isNotEmpty();
        // First retained point's cost must be ≥ 10% of final cost (10010 × 0.10 = 1001),
        // so the long 10-TL leading tail was trimmed.
        BigDecimal firstCost = r.getPoints().get(0).getCost();
        assertThat(firstCost).isNotNull();
        assertThat(firstCost.doubleValue()).isGreaterThanOrEqualTo(1001.0);
        // final cost = 10 + 10000 = 10010
        BigDecimal lastCost = r.getPoints().get(r.getPoints().size() - 1).getCost();
        assertThat(lastCost).isEqualByComparingTo("10010.00");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // floorVal firstEntry fallback (line 949-950): buyDate BEFORE first series
    // entry → floorEntry(date) is null → firstEntry() used for the base.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void computeSeries_buyDateBeforeSeriesStart_usesFirstEntryBase() {
        LocalDate today = LocalDate.now(TR);
        LocalDate buyDate = today.minusYears(3); // long before the series start

        PortfolioHoldingResponse h = holding("ETHX", AssetType.CRYPTO,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        // no qty → effQty null → ratio fallback for "actual"
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // Series starts AFTER buyDate; earliest entry = 200, last = 400.
        // floorEntry(buyDate) == null → firstEntry (200) used as base; today value 400 → ×2.
        TreeMap<LocalDate, BigDecimal> s = new TreeMap<>();
        s.put(today.minusMonths(6), new BigDecimal("200"));
        s.put(today, new BigDecimal("400"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.CRYPTO), eq("ETHX"), any(), any()))
                .thenReturn(Optional.of(s));

        WhatIfSeriesResult r = service.computeSeries(resp, "CRYPTO", "ETHX", List.of());

        assertThat(r.getAvailableScenarios()).contains("actual");
        // today point: spotTodayMv (marketValue 1000) snapshot is used → 1000.
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getActual()).isEqualByComparingTo("1000.00");
        // An EARLIER point (before today) uses ratio off the firstEntry base (200):
        // 1000 × (200/200) = 1000 at the earliest point.
        WhatIfSeriesPoint first = r.getPoints().get(0);
        assertThat(first.getActual()).isEqualByComparingTo("1000.00");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // parseBenchmarks needsFx branches via computeSimSeries:
    //   STOCK|AAPL  → STOCK && !.IS → needsFx true
    //   FUTURE|ES=F → FUTURE && contains "=F" → needsFx true
    // Both benchmark series populated so they survive into availableBenchmarks.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void computeSeries_needsFxBenchmarks_stockAndFuture_parsedAndConverted() {
        LocalDate today = LocalDate.now(TR);
        LocalDate buyDate = today.minusMonths(4);

        // The base position is a TL crypto so usd/base FX is needed to convert benchmarks.
        PortfolioHoldingResponse h = holding("BTC", AssetType.CRYPTO,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // USD/TRY series so needsFx conversion (usdT & usdBase) is available.
        twoPointSeries(AssetType.FX, "USD", buyDate, "20", today, "40"); // ×2

        // needsFx benchmarks
        twoPointSeries(AssetType.STOCK, "AAPL", buyDate, "100", today, "150");   // ×1.5 (USD)
        twoPointSeries(AssetType.FUTURE, "ES=F", buyDate, "4000", today, "6000"); // ×1.5 (USD)

        WhatIfSeriesResult r = service.computeSeries(resp, "CRYPTO", "BTC",
                List.of("STOCK|AAPL", "FUTURE|ES=F"));

        assertThat(r.getAvailableBenchmarks()).contains("STOCK|AAPL", "FUTURE|ES=F");
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        assertThat(last.getExtra()).isNotNull();
        // AAPL = 1000 × (150/100) × (40/20) = 1000 × 1.5 × 2 = 3000
        assertThat(last.getExtra().get("STOCK|AAPL")).isEqualByComparingTo("3000.00");
        // ES=F = 1000 × (6000/4000) × (40/20) = 1000 × 1.5 × 2 = 3000
        assertThat(last.getExtra().get("FUTURE|ES=F")).isEqualByComparingTo("3000.00");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // depositFactor interior compounding loop (lines 1091-1104): multiple rate
    // points strictly BETWEEN buyDate and today force the per-interval Math.pow
    // accumulation (days>0 branch) plus the tail. Exercised through compute()'s
    // deposit scenario (List<Pos> path) where date is well before today.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void compute_depositMultipleRatePoints_compoundsInteriorIntervals() {
        LocalDate today = LocalDate.now(TR);
        LocalDate buyDate = today.minusDays(400);

        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1000"), "TRY", buyDate.atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // Rate points: one before buyDate (sets currentRate), two strictly inside
        // (buyDate, today) → interior loop runs days>0 compounding for each.
        EconomySeriesPoint before = ratePoint("30", buyDate.minusDays(10));
        EconomySeriesPoint mid1 = ratePoint("50", buyDate.plusDays(100));
        EconomySeriesPoint mid2 = ratePoint("60", buyDate.plusDays(250));
        when(economyDataPort.fetchSeries(eq("TP.TRY.MT02"), any(), any()))
                .thenReturn(List.of(before, mid1, mid2));

        com.finance.portal.portfolio.application.whatif.WhatIfScenario dep =
                service.compute(resp).getScenarios().stream()
                        .filter(s -> "deposit".equals(s.getKey()))
                        .findFirst().orElseThrow();

        assertThat(dep.isAvailable()).isTrue();
        // Compounded over ~400 days with rates 30→50→60 → meaningfully above principal.
        assertThat(dep.getValue().doubleValue()).isGreaterThan(1300.0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // computeSeries single position but series base value ZERO at today's floor
    // for gold etc. is covered elsewhere; here we cover the "actual via ratio
    // fallback" historical branch (effQty null, spotTodayMv only at today, an
    // earlier point uses ratio(costTl, v, assetBase)).
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    void computeSeries_historicalActualUsesRatioWhenNoEffQty() {
        LocalDate today = LocalDate.now(TR);
        LocalDate buyDate = today.minusMonths(8);

        PortfolioHoldingResponse h = holding("FUNDZ", AssetType.FUND,
                new BigDecimal("1000"), new BigDecimal("2000"), "TRY", buyDate.atStartOfDay());
        // no totalQuantity → effQty null → historical points use ratio fallback
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        // Fund series: base 50 at buyDate, 100 at today → ratio doubles cost on history.
        twoPointSeries(AssetType.FUND, "FUNDZ", buyDate, "50", today, "100");

        WhatIfSeriesResult r = service.computeSeries(resp, "FUND", "FUNDZ", List.of());

        assertThat(r.getAvailableScenarios()).contains("actual");
        WhatIfSeriesPoint last = r.getPoints().get(r.getPoints().size() - 1);
        // today → spotTodayMv snapshot = marketValue 2000
        assertThat(last.getActual()).isEqualByComparingTo("2000.00");
        // earliest point (buyDate) → ratio(1000, 50, 50) = 1000 (historical, not snapshot)
        WhatIfSeriesPoint first = r.getPoints().get(0);
        assertThat(first.getActual()).isEqualByComparingTo("1000.00");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Stub a 2-point daily-close series for one asset type+symbol. */
    private void twoPointSeries(AssetType type, String symbol,
                                LocalDate d1, String v1, LocalDate d2, String v2) {
        TreeMap<LocalDate, BigDecimal> s = new TreeMap<>();
        s.put(d1, new BigDecimal(v1));
        s.put(d2, new BigDecimal(v2));
        when(pricePort.fetchDailyClosePrices(eq(type), eq(symbol), any(), any()))
                .thenReturn(Optional.of(s));
    }

    private EconomySeriesPoint ratePoint(String pct, LocalDate date) {
        long unix = date.atStartOfDay(TR).toEpochSecond();
        return new EconomySeriesPoint("rate", new BigDecimal(pct), unix);
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
