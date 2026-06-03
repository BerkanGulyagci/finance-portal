package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.viop.valuation.ViopValuationService;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage focused companion to {@link PortfolioPerformanceCalculatorTest}.
 * Targets uncovered arms: FUTURE valuation, BOND (gold / non-gold) effective price,
 * COUPON_INCOME skip, SELL with no open position, full-close position removal,
 * excludedKeys skip, null-argument defensive paths, and the static helpers
 * (isGoldBondSymbol / positionKey / displaySymbol / priceOnDay value-null).
 */
class PortfolioPerformanceCalculatorMoreTest {

    private EvdsBondService evdsBondService;
    private ViopContractSpecRegistry specRegistry;
    private PortfolioPerformanceCalculator calculator;

    @BeforeEach
    void setUp() {
        evdsBondService = Mockito.mock(EvdsBondService.class);
        specRegistry = Mockito.mock(ViopContractSpecRegistry.class);
        Mockito.lenient()
                .when(specRegistry.resolveOrFallback(ArgumentMatchers.anyString()))
                .thenReturn(ViopContractSpec.fallback("TEST"));
        calculator = new PortfolioPerformanceCalculator(
                evdsBondService, specRegistry, new ViopValuationService());
    }

    // ── isGoldBondSymbol ────────────────────────────────────────────────────

    @Test
    @DisplayName("isGoldBondSymbol: null ve blank için false (kısa devre)")
    void isGoldBondSymbol_nullOrBlank() {
        assertThat(calculator.isGoldBondSymbol(null)).isFalse();
        assertThat(calculator.isGoldBondSymbol("   ")).isFalse();
        Mockito.verifyNoInteractions(evdsBondService);
    }

    @Test
    @DisplayName("isGoldBondSymbol: GOLD_INDEXED_BOND kategorisi true")
    void isGoldBondSymbol_goldCategory() {
        EvdsBondInstrument gold = new EvdsBondInstrument();
        gold.setCategory(BondCategory.GOLD_INDEXED_BOND);
        Mockito.when(evdsBondService.getEvdsBondDetail("AURA")).thenReturn(gold);
        assertThat(calculator.isGoldBondSymbol("AURA")).isTrue();
    }

    @Test
    @DisplayName("isGoldBondSymbol: gold olmayan kategori ve null detay false")
    void isGoldBondSymbol_notGoldAndNullDetail() {
        EvdsBondInstrument coupon = new EvdsBondInstrument();
        coupon.setCategory(BondCategory.FIXED_COUPON_BOND);
        Mockito.when(evdsBondService.getEvdsBondDetail("CPN")).thenReturn(coupon);
        Mockito.when(evdsBondService.getEvdsBondDetail("MISSING")).thenReturn(null);

        assertThat(calculator.isGoldBondSymbol("CPN")).isFalse();
        assertThat(calculator.isGoldBondSymbol("MISSING")).isFalse();
    }

    @Test
    @DisplayName("isGoldBondSymbol: detay servisi patlarsa false (catch)")
    void isGoldBondSymbol_exceptionSwallowed() {
        Mockito.when(evdsBondService.getEvdsBondDetail("BOOM"))
                .thenThrow(new RuntimeException("evds down"));
        assertThat(calculator.isGoldBondSymbol("BOOM")).isFalse();
    }

    // ── positionKey / displaySymbol ─────────────────────────────────────────

    @Test
    @DisplayName("positionKey: FUTURE yön ekler; null direction LONG'a düşer")
    void positionKey_futureDirection() {
        PortfolioTransaction shortTx = tx("F_XU0300625", AssetType.FUTURE,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        shortTx.setDirection("short");
        String shortKey = PortfolioPerformanceCalculator.positionKey(shortTx);
        assertThat(shortKey).contains("::FUTURE::SHORT");

        PortfolioTransaction longDefault = tx("F_XU0300625", AssetType.FUTURE,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        longDefault.setDirection(null);
        assertThat(PortfolioPerformanceCalculator.positionKey(longDefault)).endsWith("::FUTURE::LONG");

        PortfolioTransaction blankDir = tx("F_XU0300625", AssetType.FUTURE,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        blankDir.setDirection("  ");
        assertThat(PortfolioPerformanceCalculator.positionKey(blankDir)).endsWith("::FUTURE::LONG");
    }

    @Test
    @DisplayName("positionKey: non-FUTURE null symbol boş parçaya düşer")
    void positionKey_nullSymbolNonFuture() {
        PortfolioTransaction t = tx(null, AssetType.STOCK,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        assertThat(PortfolioPerformanceCalculator.positionKey(t)).isEqualTo("::STOCK");
    }

    @Test
    @DisplayName("displaySymbol: FUTURE normalize, non-FUTURE ham sembol döner")
    void displaySymbol_futureAndPlain() {
        PortfolioTransaction stock = tx("THYAO", AssetType.STOCK,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        assertThat(PortfolioPerformanceCalculator.displaySymbol(stock)).isEqualTo("THYAO");

        PortfolioTransaction fut = tx("F_XU0300625", AssetType.FUTURE,
                TransactionType.BUY, LocalDate.of(2026, 4, 1).atStartOfDay(), "1", "100");
        // Normalize'in tam çıktısı serviste; sadece null-değil garantisi yeterli (fallback dahil).
        assertThat(PortfolioPerformanceCalculator.displaySymbol(fut)).isNotNull();
    }

    // ── priceOnDay: değeri null olan entry ──────────────────────────────────

    @Test
    @DisplayName("priceOnDay: floorEntry'nin değeri null ise null döner")
    void priceOnDay_entryValueNull() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 4, 1), null);
        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 4, 2)))
                .isNull();
    }

    // ── calculate: FUTURE valuation branch ──────────────────────────────────

    @Test
    @DisplayName("calculate: FUTURE pozisyonu margin + pnl ile değerlenir")
    void calculate_futurePosition() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);

        PortfolioTransaction fut = tx("F_XU0300625", AssetType.FUTURE,
                TransactionType.BUY, d1.atStartOfDay(), "1", "100");
        List<PortfolioTransaction> txs = List.of(fut);

        String key = PortfolioPerformanceCalculator.positionKey(fut);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("110"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        assertThat(points).hasSize(1);
        // avgEntry=100, multiplier=1, marginRate=0.15 → margin=15
        // pnl=(110-100)*1*1=10 → contribution=25 ; cost=margin=15
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("25");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("15");
        assertThat(points.get(0).getProfitLoss()).isEqualByComparingTo("10");
    }

    // ── calculate: BOND non-gold (/100) vs gold (no /100) ───────────────────

    @Test
    @DisplayName("calculate: non-gold BOND fiyatı 100 nominal üzerinden bölünür")
    void calculate_bondNonGoldParScale() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        // getEvdsBondDetail → null ⇒ isGoldBond false ⇒ /100 uygulanır
        Mockito.when(evdsBondService.getEvdsBondDetail(ArgumentMatchers.anyString())).thenReturn(null);

        PortfolioTransaction bond = tx("TRD070727", AssetType.BOND,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        List<PortfolioTransaction> txs = List.of(bond);

        String key = PortfolioPerformanceCalculator.positionKey(bond);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // effectivePrice = 100/100 = 1 ; mv = 1 * 10 = 10
        // cost basis: buy effectivePrice 100/100=1 → 10*1 = 10
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("10");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("10");
        assertThat(points.get(0).getProfitLoss()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("calculate: GOLD BOND fiyatı bölünmez (adet/gram)")
    void calculate_goldBondNoParScale() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        EvdsBondInstrument gold = new EvdsBondInstrument();
        gold.setCategory(BondCategory.GOLD_INDEXED_BOND);
        Mockito.when(evdsBondService.getEvdsBondDetail(ArgumentMatchers.anyString())).thenReturn(gold);

        PortfolioTransaction bond = tx("TRGOLD0727", AssetType.BOND,
                TransactionType.BUY, d1.atStartOfDay(), "2", "2500");
        List<PortfolioTransaction> txs = List.of(bond);

        String key = PortfolioPerformanceCalculator.positionKey(bond);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("2600"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // No /100: mv = 2600 * 2 = 5200 ; cost = 2 * 2500 = 5000
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("5200");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("5000");
        assertThat(points.get(0).getProfitLoss()).isEqualByComparingTo("200");
    }

    // ── calculate: COUPON_INCOME apply skip ─────────────────────────────────

    @Test
    @DisplayName("calculate: COUPON_INCOME pozisyonu değiştirmez")
    void calculate_couponIncomeSkipped() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        Mockito.when(evdsBondService.getEvdsBondDetail(ArgumentMatchers.anyString())).thenReturn(null);

        PortfolioTransaction buy = tx("TRD070727", AssetType.BOND,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        PortfolioTransaction coupon = tx("TRD070727", AssetType.BOND,
                TransactionType.COUPON_INCOME, d1.atStartOfDay().plusHours(1), "50", "1");
        List<PortfolioTransaction> txs = List.of(buy, coupon);

        String key = PortfolioPerformanceCalculator.positionKey(buy);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // Coupon openQuantity'yi etkilemez: hala 10 adet, mv = 1 * 10 = 10
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("10");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("10");
    }

    // ── calculate: SELL with no open position (early return) ─────────────────

    @Test
    @DisplayName("calculate: açık pozisyon yokken SELL no-op")
    void calculate_sellWithNoOpenPosition() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);

        PortfolioTransaction sellOnly = tx("THYAO", AssetType.STOCK,
                TransactionType.SELL, d1.atStartOfDay(), "5", "100");
        List<PortfolioTransaction> txs = List.of(sellOnly);

        String key = PortfolioPerformanceCalculator.positionKey(sellOnly);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // SELL apply early-return: openQuantity <= 0 → pozisyon openPositions'tan çıkarılır.
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("0");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("0");
    }

    // ── calculate: full SELL closes position (qty >= openQuantity, remove) ────

    @Test
    @DisplayName("calculate: tam SELL pozisyonu kapatır (openQuantity<=0 → remove)")
    void calculate_fullSellClosesPosition() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 4, 2);

        PortfolioTransaction buy = tx("THYAO", AssetType.STOCK,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        PortfolioTransaction sellAll = tx("THYAO", AssetType.STOCK,
                TransactionType.SELL, d2.atStartOfDay(), "10", "120");
        List<PortfolioTransaction> txs = List.of(buy, sellAll);

        String key = PortfolioPerformanceCalculator.positionKey(buy);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        prices.put(d2, new BigDecimal("120"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d2, series, new HashSet<>(), new ArrayList<>());

        assertThat(points).hasSize(2);
        // Day1 açık 10@100 → mv 1000 ; Day2 tam satış → pozisyon kapalı → mv 0
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("1000");
        assertThat(points.get(1).getMarketValue()).isEqualByComparingTo("0");
        assertThat(points.get(1).getTotalCost()).isEqualByComparingTo("0");
    }

    // ── calculate: oversell (qty > openQuantity) full cost basis arm ─────────

    @Test
    @DisplayName("calculate: oversell (qty>openQuantity) tüm maliyeti düşer")
    void calculate_oversellUsesFullCostBasis() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);

        PortfolioTransaction buy = tx("GARAN", AssetType.STOCK,
                TransactionType.BUY, d1.atStartOfDay(), "5", "40");
        PortfolioTransaction oversell = tx("GARAN", AssetType.STOCK,
                TransactionType.SELL, d1.atStartOfDay().plusHours(2), "8", "50");
        List<PortfolioTransaction> txs = List.of(buy, oversell);

        String key = PortfolioPerformanceCalculator.positionKey(buy);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("45"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // qty(8) >= openQuantity(5) → soldCostBasis = full → pozisyon tamamen kapanır
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("0");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("0");
    }

    // ── calculate: excludedKeys provided → skipped in both loops ─────────────

    @Test
    @DisplayName("calculate: önceden hariç tutulan key işleme alınmaz")
    void calculate_preExcludedKeySkipped() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);

        PortfolioTransaction buy = tx("THYAO", AssetType.STOCK,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        List<PortfolioTransaction> txs = List.of(buy);

        String key = PortfolioPerformanceCalculator.positionKey(buy);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        Set<String> excluded = new HashSet<>();
        excluded.add(key);
        List<ExcludedPerformanceAsset> excludedOut = new ArrayList<>();

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, excluded, excludedOut);

        // key excludedKeys'te → hiç pozisyon açılmaz, mv 0, ek hariç tutma yok
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("0");
        assertThat(excludedOut).isEmpty();
    }

    // ── calculate: null transactions + null excluded sets/out ────────────────

    @Test
    @DisplayName("calculate: null transactions ve null excluded argümanları güvenli")
    void calculate_nullArgumentsSafe() {
        LocalDate day = LocalDate.of(2026, 5, 1);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                null, day, day, Map.of(), null, null);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("0");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("0");
        assertThat(points.get(0).getProfitLoss()).isEqualByComparingTo("0");
    }

    // ── calculate: tx with null date is skipped (break) + commission added ────

    @Test
    @DisplayName("calculate: transactionDate null olan işlem replay'de atlanır")
    void calculate_nullTransactionDateBreak() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);

        PortfolioTransaction dated = tx("THYAO", AssetType.STOCK,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        dated.setCommission(new BigDecimal("5")); // commission != null arm
        PortfolioTransaction noDate = tx("THYAO", AssetType.STOCK,
                TransactionType.BUY, d1.atStartOfDay(), "10", "100");
        noDate.setTransactionDate(null);

        // null date son sıraya gider (nullsLast) → break tetiklenir, işlenmez.
        List<PortfolioTransaction> txs = List.of(dated, noDate);

        String key = PortfolioPerformanceCalculator.positionKey(dated);
        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d1, series, new HashSet<>(), new ArrayList<>());

        // Sadece tarihli işlem işlenir: 10 adet, mv = 1000.
        // cost = 10*100 + 5 komisyon = 1005.
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("1000");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("1005");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static PortfolioTransaction tx(String symbol, AssetType type, TransactionType side,
                                           LocalDateTime when, String qty, String price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(type);
        t.setTransactionType(side);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }
}
