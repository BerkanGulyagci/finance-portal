package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
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

    private BondHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new BondHoldingEnricher(evdsBondService, eurobondService);
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

        PortfolioHoldingResponse h = holding("TRD070727K10", new BigDecimal("10"), new BigDecimal("1000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("105.50");
        // 105.50 × 10 = 1055.0000
        assertThat(h.getMarketValue()).isEqualByComparingTo("1055.0000");
        // 1055.0000 − 1000 = 55.0000
        assertThat(h.getProfitLoss()).isEqualByComparingTo("55.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getChange()).isEqualByComparingTo("0.50");
        assertThat(h.getChangePercent()).isEqualByComparingTo("0.47");
        assertThat(h.getName()).isEqualTo("TRD070727K10 · Devlet Tahvili");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("evds: indicator yoksa IllegalStateException fırlatır")
    void evds_missingIndicator_throws() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        EvdsBondInstrument bond = new EvdsBondInstrument();   // indicatorValue null
        when(evdsBondService.getEvdsBondDetail("X")).thenReturn(bond);

        assertThatThrownBy(() -> enricher.enrich(holding("X", BigDecimal.ONE, BigDecimal.ZERO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bond EVDS indicator unavailable");
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

        PortfolioHoldingResponse h = holding("us900123al40", new BigDecimal("2"), new BigDecimal("6000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3500.25");
        // 3500.25 × 2 = 7000.5000
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

}
