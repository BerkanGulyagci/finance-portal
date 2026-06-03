package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Ek branch kapsamı: {@link BondHoldingEnricherTest} happy-path/fail-soft ana akışları
 * kapsıyor; bu sınıf JaCoCo'da hâlâ kırmızı/sarı kalan kenarları (history fallback döngüsü,
 * null-symbol dispatch, null-bond kategori, qty/cost/name/fxRate null kolları, boş close
 * serisi, eurobond grafik exception) karakterizasyon olarak hedefler. Davranış değişmez;
 * yalnız erişilmemiş dallar tetiklenir.
 *
 * <p>NOT: {@code guessFxBondCurrency / lookupFxRateToTry / lookupGoldGramTry} private yardımcıları
 * sınıf içinden hiçbir erişilebilir yoldan çağrılmıyor (ölü kod) — public {@code enrich(...)}
 * API'siyle tetiklenemezler, bu yüzden burada kapsanmazlar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BondHoldingEnricherMoreTest {

    @Mock EvdsBondService evdsBondService;
    @Mock EurobondService eurobondService;
    @Mock MarketFxService marketFxService;
    @Mock GoldMarketService goldMarketService;

    private BondHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new BondHoldingEnricher(evdsBondService, eurobondService,
                marketFxService, goldMarketService);
    }

    private PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    // =========================================================================
    // Dispatch: symbol == null → enrich() ve enrichEvdsBond() boş-kod kolları
    // (BondHoldingEnricher L85 false, L97 false)
    // =========================================================================

    @Test
    @DisplayName("dispatch: symbol null → kod boş, EVDS branch (eurobond ISIN listesinde değil)")
    void enrich_nullSymbol_goesToEvdsWithEmptyCode() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));
        // code "" ile EVDS detayı sorgulanır; null dönsün → fail-soft (price yok), type null
        when(evdsBondService.getEvdsBondDetail("")).thenReturn(null);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(null, BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // Hem indicator hem history yok → currency TRY, price null bırakılır.
        assertThat(h.getCurrentPrice()).isNull();
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getCurrency()).isEqualTo("TRY");
        // type null → fail-soft return'da name dokunulmaz.
        assertThat(h.getName()).isNull();
    }

    // =========================================================================
    // History fallback: indicator yok + bond null → son pozitif kapanış kullanılır
    // (L116 false, L130 true-arm, L131-137 loop, L154 false, L198 fallbackDate!=null,
    //  L204 true → "(vadesi geçti)")
    // =========================================================================

    @Test
    @DisplayName("evds: bond null + history → son pozitif kapanış fallback, name '(vadesi geçti)'")
    void evds_historyFallback_whenBondNull_usesLastPositiveClose() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // bond null → price/type/lu hepsi null kalır, category null
        when(evdsBondService.getEvdsBondDetail("TRT999999T99")).thenReturn(null);

        // En yeni nokta indicator null (atlanır) → bir önceki pozitif (90.5) seçilir.
        // (Döngü sondan başa: i=1 null skip, i=0 pozitif al/break → L133 her iki kolu.)
        List<EvdsBondHistoryPoint> hist = new ArrayList<>();
        hist.add(new EvdsBondHistoryPoint(LocalDate.of(2026, 1, 5), "05-01-2026",
                "TRT999999T99", new BigDecimal("90.5")));
        hist.add(new EvdsBondHistoryPoint(LocalDate.of(2026, 1, 6), "06-01-2026",
                "TRT999999T99", null));
        when(evdsBondService.getEvdsBondHistory("TRT999999T99", BondPeriod.ONE_YEAR)).thenReturn(hist);

        PortfolioHoldingResponse h = holding("TRT999999T99",
                new BigDecimal("10000"), new BigDecimal("9000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("90.5");
        // mv = 10000 × 90.5 / 100 = 9050
        assertThat(h.getMarketValue()).isEqualByComparingTo("9050.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("50.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        // bond null → category set edilmez
        assertThat(h.getCategory()).isNull();
        // type null + usedHistoryFallback → L204 → "kod (vadesi geçti)"
        assertThat(h.getName()).isEqualTo("TRT999999T99 (vadesi geçti)");
        // lu null → fallbackDate (2026-01-05) günbaşı kullanılır (L198)
        assertThat(h.getAsOf()).isEqualTo(LocalDate.of(2026, 1, 5).atStartOfDay());
        // closes = [90.5] (null filtrelendi) → 52w doldurulur
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("90.5");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("90.5");
    }

    // =========================================================================
    // History fallback + type mevcut: indicator ≤ 0 → fallback, suffix "(vadesi geçti)"
    // (L130 price.compareTo(ZERO)<=0 true-arm, L142 ikinci operand, L201 true, L202 true-arm)
    // =========================================================================

    @Test
    @DisplayName("evds: indicator=0 + type + history → '· type (vadesi geçti)' suffix")
    void evds_historyFallback_zeroIndicator_withType_appendsVadesiGecti() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(BigDecimal.ZERO);   // ≤ 0 → fallback'i tetikler (L130/L142)
        bond.setType("Devlet Tahvili");
        when(evdsBondService.getEvdsBondDetail("TRD070727K10")).thenReturn(bond);

        List<EvdsBondHistoryPoint> hist = new ArrayList<>();
        hist.add(new EvdsBondHistoryPoint(LocalDate.of(2026, 2, 10), "10-02-2026",
                "TRD070727K10", new BigDecimal("102")));
        when(evdsBondService.getEvdsBondHistory("TRD070727K10", BondPeriod.ONE_YEAR)).thenReturn(hist);

        PortfolioHoldingResponse h = holding("TRD070727K10",
                new BigDecimal("10000"), new BigDecimal("10000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("102");
        assertThat(h.getMarketValue()).isEqualByComparingTo("10200.0000");
        // type + usedHistoryFallback → L202 true-arm
        assertThat(h.getName()).isEqualTo("TRD070727K10 · Devlet Tahvili (vadesi geçti)");
    }

    // =========================================================================
    // Fail-soft return + type mevcut → name = "kod · type" (L145 true → L146)
    // =========================================================================

    @Test
    @DisplayName("evds: indicator yok + history yok ama type var → fail-soft name 'kod · type'")
    void evds_failSoft_withType_setsName() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        // indicatorValue null → price yok
        bond.setType("Hazine Bonosu");
        when(evdsBondService.getEvdsBondDetail("TRB120526T16")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(List.of()); // boş → fallback yok

        PortfolioHoldingResponse h = holding("TRB120526T16", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isNull();
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getCurrency()).isEqualTo("TRY");
        // L145 true-arm → name doldurulur
        assertThat(h.getName()).isEqualTo("TRB120526T16 · Hazine Bonosu");
    }

    // =========================================================================
    // qty == null → bondMarketValue null kolu (L69 qty-null) + L175 mv==null kolu
    // =========================================================================

    @Test
    @DisplayName("evds: qty null → marketValue null (bondMarketValue qty-null kolu), price yine set")
    void evds_qtyNull_marketValueNull() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("101.25"));
        bond.setCategory(BondCategory.GOLD_INDEXED_BOND); // usesPerUnitNominalQuote → L175'e girer ama mv null
        when(evdsBondService.getEvdsBondDetail("TRT270127T15")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        // qty null → bondMarketValue(price, null) == null
        PortfolioHoldingResponse h = holding("TRT270127T15", null, new BigDecimal("1000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("101.25");
        // mv null (L69 qty-null), L175 mv!=null false → per-unit kol atlanır
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getProfitLoss()).isNull();
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getCategory()).isEqualTo("GOLD_INDEXED_BOND");
    }

    // =========================================================================
    // history non-empty ama tüm indicatorValue null → closes boş (L208 true, L213 false)
    // =========================================================================

    @Test
    @DisplayName("evds: history dolu ama tüm değerler null → closes boş, 52w/MA set edilmez")
    void evds_historyAllNull_emptyCloses_skips52w() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("100"));  // price geçerli → fallback yok
        when(evdsBondService.getEvdsBondDetail("TRT070727T13")).thenReturn(bond);

        List<EvdsBondHistoryPoint> hist = new ArrayList<>();
        hist.add(new EvdsBondHistoryPoint(LocalDate.of(2026, 3, 1), "01-03-2026", "TRT070727T13", null));
        hist.add(new EvdsBondHistoryPoint(LocalDate.of(2026, 3, 2), "02-03-2026", "TRT070727T13", null));
        when(evdsBondService.getEvdsBondHistory("TRT070727T13", BondPeriod.ONE_YEAR)).thenReturn(hist);

        PortfolioHoldingResponse h = holding("TRT070727T13", new BigDecimal("100"), new BigDecimal("90"));
        enricher.enrich(h);

        assertThat(h.getMarketValue()).isEqualByComparingTo("100.0000");
        // closes boş (hepsi filtrelendi) → L213 false-arm: 52w/MA dokunulmaz
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getFiftyTwoWeekLow()).isNull();
        assertThat(h.getMa20()).isNull();
        assertThat(h.getMa50()).isNull();
    }

    // =========================================================================
    // Eurobond: qty null + cost null + name null + fxRate null + boş chart
    // (L236 false, L243 false, L252 false→isin, L257 false→ONE, L262 false)
    // =========================================================================

    @Test
    @DisplayName("eurobond: qty/cost/name/fxRate null + boş chart → fallback kolları (mv qty=0, name=isin)")
    void eurobond_allNullishBranches() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123CK49"));

        EurobondDetail d = new EurobondDetail();
        d.setLastPriceTry(new BigDecimal("3500.25"));
        // name null → L252 isin'e düşer
        // fxRate null → L257 BigDecimal.ONE
        // changePercent null
        when(eurobondService.detail("US900123CK49")).thenReturn(d);
        when(eurobondService.chart("US900123CK49", "1Y")).thenReturn(List.of()); // boş → L262 false

        // qty null → L236 BigDecimal.ZERO ; cost null → L243 BigDecimal.ZERO
        PortfolioHoldingResponse h = holding("US900123CK49", null, null);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3500.25");
        // qty 0 → mv = 0 × 3500.25 / 100 = 0
        assertThat(h.getMarketValue()).isEqualByComparingTo("0.0000");
        // cost 0 → pl = 0 − 0 = 0
        assertThat(h.getProfitLoss()).isEqualByComparingTo("0.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        // name null → isin
        assertThat(h.getName()).isEqualTo("US900123CK49");
        // boş chart → 52w dokunulmaz
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getMa20()).isNull();
    }

    // =========================================================================
    // Eurobond: detail non-null, lastPriceTry null, name null → fail-soft, name=isin
    // (L230 d!=null & lastPriceTry==null, L232 d!=null & name==null → isin)
    // =========================================================================

    @Test
    @DisplayName("eurobond: detail var ama lastPriceTry & name null → fail-soft name=isin")
    void eurobond_priceTryNull_nameNull_failSoftUsesIsin() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123ZZ99"));

        EurobondDetail d = new EurobondDetail();
        // lastPriceTry null → core fail-soft; name null → L232 sağ kol (isin)
        when(eurobondService.detail("US900123ZZ99")).thenReturn(d);

        PortfolioHoldingResponse h = holding("US900123ZZ99", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("US900123ZZ99");
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getProfitLoss()).isNull();
    }

    // =========================================================================
    // Eurobond: chart() exception → catch swallow (L267/L268), core fields yine dolar
    // =========================================================================

    @Test
    @DisplayName("eurobond: chart() fırlatırsa 52w/MA atlanır ama core fields dolu kalır")
    void eurobond_chartThrows_isSwallowed() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));

        EurobondDetail d = new EurobondDetail();
        d.setName("Turkey 2030 USD");
        d.setLastPriceTry(new BigDecimal("3500.25"));
        d.setFxRate(new BigDecimal("35"));
        d.setChangePercent(new BigDecimal("1.2"));
        when(eurobondService.detail("US900123AL40")).thenReturn(d);
        when(eurobondService.chart("US900123AL40", "1Y"))
                .thenThrow(new RuntimeException("BI down"));

        PortfolioHoldingResponse h = holding("US900123AL40", new BigDecimal("200"), new BigDecimal("6000"));
        enricher.enrich(h);

        // core fields set edildi (try öncesi)
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3500.25");
        assertThat(h.getMarketValue()).isEqualByComparingTo("7000.5000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("1000.5000");
        assertThat(h.getName()).isEqualTo("Turkey 2030 USD");
        // exception swallow → 52w/MA dokunulmaz
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getMa20()).isNull();
    }
}
