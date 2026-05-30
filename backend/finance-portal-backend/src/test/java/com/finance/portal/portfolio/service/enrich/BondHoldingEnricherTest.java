package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterization: BondHoldingEnricher davranışı eski
 * {@code PortfolioHoldingMarketEnricher.enrichBondHolding/enrichEurobondHolding} ile aynı.
 * Symbol HMB ISIN listesinde varsa Eurobond branch; yoksa EVDS branch.
 */
@ExtendWith(MockitoExtension.class)
class BondHoldingEnricherTest {

    @Mock EvdsBondService evdsBondService;
    @Mock EurobondService eurobondService;
    @Mock com.finance.portal.market.application.service.MarketFxService marketFxService;
    @Mock com.finance.portal.market.application.gold.GoldMarketService goldMarketService;

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
    // EVDS bond branch
    // =========================================================================

    @Test
    @DisplayName("evds: indicator + history → mv, pl, change, 52w, MA")
    void evds_fullEnrichment() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("105.50"));
        bond.setDailyChange(new BigDecimal("0.50"));
        bond.setDailyChangePercent(new BigDecimal("0.47"));
        bond.setLastUpdated(LocalDate.of(2026, 5, 26));
        bond.setType("Devlet Tahvili");
        when(evdsBondService.getEvdsBondDetail("TRD070727K10")).thenReturn(bond);

        List<EvdsBondHistoryPoint> hist = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            EvdsBondHistoryPoint p = new EvdsBondHistoryPoint();
            p.setIndicatorValue(new BigDecimal(i));
            hist.add(p);
        }
        when(evdsBondService.getEvdsBondHistory("TRD070727K10", BondPeriod.ONE_YEAR)).thenReturn(hist);

        // TCMB konvansiyonu: 10.000 TL nominal kuponlu DT alış (price 100 girilmiş)
        // → cost = 10.000 × 100/100 = 10.000 TL (HoldingsBuilder hesabı; burada doğrudan veriliyor)
        PortfolioHoldingResponse h = holding("TRD070727K10", new BigDecimal("10000"), new BigDecimal("10000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("105.50");
        // mv = 10.000 nominal × 105.50/100 = 10.550 TL
        assertThat(h.getMarketValue()).isEqualByComparingTo("10550.0000");
        // 10.550 − 10.000 = 550
        assertThat(h.getProfitLoss()).isEqualByComparingTo("550.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getChange()).isEqualByComparingTo("0.50");
        assertThat(h.getChangePercent()).isEqualByComparingTo("0.47");
        assertThat(h.getName()).isEqualTo("TRD070727K10 · Devlet Tahvili");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("evds: indicator yoksa fail-soft — mv/pl null, currency=TRY")
    void evds_missingIndicator_failSoft() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        EvdsBondInstrument bond = new EvdsBondInstrument();   // indicatorValue null
        when(evdsBondService.getEvdsBondDetail("X")).thenReturn(bond);

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isNull();
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getProfitLoss()).isNull();
        assertThat(h.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("evds: history fırlatırsa core fields yine doldurulur")
    void evds_historyThrows_isSwallowed() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("100"));
        when(evdsBondService.getEvdsBondDetail("X")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenThrow(new RuntimeException("EVDS down"));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
        assertThat(h.getMa20()).isNull();
    }

    // =========================================================================
    // Eurobond branch (sembol HMB ISIN listesinde)
    // =========================================================================

    @Test
    @DisplayName("eurobond: ISIN HMB listesinde → BI fiyatı (TL) ile mv/pl/52w/MA")
    void eurobond_fullEnrichment() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));

        EurobondDetail d = new EurobondDetail();
        d.setName("Turkey 2030 USD");
        d.setLastPriceTry(new BigDecimal("3500.25"));
        d.setFxRate(new BigDecimal("35"));
        d.setChangePercent(new BigDecimal("1.2"));
        when(eurobondService.detail("US900123AL40")).thenReturn(d);

        List<EurobondChartPoint> chart = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            chart.add(new EurobondChartPoint("2026-01-" + String.format("%02d", i),
                    new BigDecimal(i), null, null, null));
        }
        when(eurobondService.chart("US900123AL40", "1Y")).thenReturn(chart);

        // Eurobond BI quote'u da % nominal — 200 nominal × 3500.25/100 = 7.000,50 TL
        PortfolioHoldingResponse h = holding("us900123al40", new BigDecimal("200"), new BigDecimal("6000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3500.25");
        // mv = 200 × 3500.25 / 100 = 7000.5000
        assertThat(h.getMarketValue()).isEqualByComparingTo("7000.5000");
        // 7000.5000 − 6000 = 1000.5000
        assertThat(h.getProfitLoss()).isEqualByComparingTo("1000.5000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Turkey 2030 USD");
        assertThat(h.getChangePercent()).isEqualByComparingTo("1.2");
        // 52w high/low: closes × fxRate 35 → 1×35=35 .. 20×35=700
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("35.0000");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("700.0000");
    }

    @Test
    @DisplayName("eurobond: detail null → name=ISIN, currency=TRY, mv/pl null (fail-soft)")
    void eurobond_detailNull_failSoft() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123CK49"));
        when(eurobondService.detail("US900123CK49")).thenReturn(null);

        PortfolioHoldingResponse h = holding("US900123CK49", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("US900123CK49");
        assertThat(h.getMarketValue()).isNull();
        assertThat(h.getProfitLoss()).isNull();
    }

    @Test
    @DisplayName("eurobond: lastPriceTry null → core fail-soft branch")
    void eurobond_priceTryNull_failSoft() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123CK49"));

        EurobondDetail d = new EurobondDetail();
        d.setName("Turkey Bond");
        // lastPriceTry null
        when(eurobondService.detail("US900123CK49")).thenReturn(d);

        PortfolioHoldingResponse h = holding("US900123CK49", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Turkey Bond");
    }

    @Test
    @DisplayName("dispatch: symbol HMB listesinde değilse EVDS branch'e gider")
    void dispatch_unknownIsin_goesToEvds() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("50"));
        when(evdsBondService.getEvdsBondDetail("X")).thenReturn(bond);

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // EVDS branch fiyatı set etti → eurobond service.detail asla çağrılmamış.
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("50");
    }

    // =========================================================================
    // Tier 2 — TÜFE-endeksli BOND zenginleştirmesi (nominal MV, ek çarpan YOK)
    // =========================================================================

    @Test
    @DisplayName("evds TÜFE-endeksli: mv = qty × price / 100 — EVDS değeri zaten nominal, ek TÜFE çarpanı YOK")
    void evds_inflationIndexed_usesNominalEvdsValueDirectly() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        // EVDS Gösterge Değeri zaten nominal/endeksli — ihraçtan bugüne enflasyonu içinde taşır.
        bond.setIndicatorValue(new BigDecimal("1223.262"));
        bond.setCategory(BondCategory.INFLATION_INDEXED_BOND);
        bond.setType("Devlet Tahvili");
        when(evdsBondService.getEvdsBondDetail("TRT070727T13")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        // 10.000 nominal alış, alış-dönemi nominal TL maliyet 110.063 (≈ 10000 × 1100.6/100).
        PortfolioHoldingResponse h = holding("TRT070727T13",
                new BigDecimal("10000"), new BigDecimal("110063"));
        h.setFirstBuyDate(java.time.LocalDateTime.of(2026, 3, 1, 0, 0));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("1223.262");
        // mv = 10000 × 1223.262 / 100 = 122.326,20 (ek çarpan YOK — çifte sayım olmaz)
        assertThat(h.getMarketValue()).isEqualByComparingTo("122326.2000");
        // K/Z = 122.326,20 − 110.063 = 12.263,20 (bondun kendi nominal getirisi)
        assertThat(h.getProfitLoss()).isEqualByComparingTo("12263.2000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("evds TÜFE-strip: deflator hiç çağrılmaz — mv düz nominal qty × price / 100")
    void evds_inflationStrip_noDeflatorInteraction() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("95"));
        bond.setCategory(BondCategory.INFLATION_COUPON_STRIP);
        when(evdsBondService.getEvdsBondDetail("TRT030626K26")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("TRT030626K26",
                new BigDecimal("10000"), new BigDecimal("900"));
        h.setFirstBuyDate(java.time.LocalDateTime.of(2023, 3, 8, 0, 0));
        enricher.enrich(h);

        // mv = 10.000 × 95 / 100 = 9.500 (deflator artık enjekte bile edilmiyor)
        assertThat(h.getMarketValue()).isEqualByComparingTo("9500.0000");
    }

    // =========================================================================
    // Tier 3 — Yabancı para / Altın cinsli bondlar
    // =========================================================================

    @Test
    @DisplayName("evds FX cinsli bond: EVDS değeri TL — dış FX çevirisi YOK, currency=TRY")
    void evds_fxBond_appliesUsdRate() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("95"));
        bond.setCategory(BondCategory.FX_DENOMINATED_BOND);
        when(evdsBondService.getEvdsBondDetail("TRT220726F11")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("TRT220726F11",
                new BigDecimal("10000"), new BigDecimal("9000"));
        enricher.enrich(h);

        // Kullanıcı tercihi: EVDS değeri zaten TL — mv = 10.000 × 95 / 100 = 9.500
        assertThat(h.getMarketValue()).isEqualByComparingTo("9500.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("500.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("evds Altın bond: mv = adet × price (EVDS kendi fiyatı, gram spot YOK), /100 YOK")
    void evds_goldBond_appliesGramRate() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("3500"));
        bond.setCategory(BondCategory.GOLD_INDEXED_BOND);
        when(evdsBondService.getEvdsBondDetail("TRT270127T15")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        // 100 adet (= 100 gram) altın senedi, maliyet 300.000 TL
        PortfolioHoldingResponse h = holding("TRT270127T15",
                new BigDecimal("100"), new BigDecimal("300000"));
        enricher.enrich(h);

        // Kullanıcı tercihi: gram altın spot ile çevirme — mv = 100 × 3500 = 350.000 (raw EVDS değer, /100 YOK)
        assertThat(h.getMarketValue()).isEqualByComparingTo("350000.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("50000.0000");
    }

    @Test
    @DisplayName("evds TÜFE-endeksli olmayan bond: TÜFE çarpanı uygulanmaz (Tier 1 davranışı korunur)")
    void evds_normalBond_doesNotApplyCpi() {
        when(eurobondService.currentIsins()).thenReturn(List.of());

        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("90"));
        bond.setCategory(BondCategory.FIXED_COUPON_BOND);  // TÜFE'siz
        when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);
        when(evdsBondService.getEvdsBondHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("TRT240227T17",
                new BigDecimal("10000"), new BigDecimal("8500"));
        h.setFirstBuyDate(java.time.LocalDateTime.of(2024, 1, 1, 0, 0));
        enricher.enrich(h);

        // mv = 10.000 × 90 / 100 = 9.000 (TÜFE çarpanı YOK)
        assertThat(h.getMarketValue()).isEqualByComparingTo("9000.0000");
        // Deflator hiç çağrılmamış olmalı — mock zaten setup edilmedi
    }
}
