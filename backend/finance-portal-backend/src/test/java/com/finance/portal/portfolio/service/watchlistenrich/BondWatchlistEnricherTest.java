package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondWatchlistEnricherTest {

    @Mock EvdsBondService evdsBondService;
    @Mock EurobondService eurobondService;

    private BondWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new BondWatchlistEnricher(evdsBondService, eurobondService);
    }

    @Test
    void evds_corePath() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIndicatorValue(new BigDecimal("105.5"));
        bond.setDailyChange(new BigDecimal("0.5"));
        bond.setDailyChangePercent(new BigDecimal("0.47"));
        bond.setRemainingDays(120);
        bond.setCouponRate(new BigDecimal("15.5"));
        when(evdsBondService.getEvdsBondDetail("TRD070727K10")).thenReturn(bond);

        List<EvdsBondHistoryPoint> hist = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            EvdsBondHistoryPoint p = new EvdsBondHistoryPoint();
            p.setIndicatorValue(new BigDecimal(i + 1));
            hist.add(p);
        }
        when(evdsBondService.getEvdsBondHistory("TRD070727K10", BondPeriod.ONE_YEAR)).thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "TRD070727K10");

        assertThat(r.getLastPrice()).isEqualByComparingTo("105.5");
        assertThat(r.getChange()).isEqualByComparingTo("0.5");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getRemainingDays()).isEqualTo(120);
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    void eurobond_corePath() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));
        EurobondDetail d = new EurobondDetail();
        d.setLastPriceTry(new BigDecimal("3500.25"));
        d.setFxRate(new BigDecimal("35"));
        d.setChangePercent(new BigDecimal("1.2"));
        when(eurobondService.detail("US900123AL40")).thenReturn(d);

        List<EurobondChartPoint> chart = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            chart.add(new EurobondChartPoint("2026-01-" + String.format("%02d", i + 1),
                    new BigDecimal(i + 1), null, null, null));
        }
        when(eurobondService.chart("US900123AL40", "1Y")).thenReturn(chart);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "us900123al40");

        assertThat(r.getLastPrice()).isEqualByComparingTo("3500.25");
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.2");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        // 20 × 35 = 700 max (scaled to 4 decimal)
        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("700.0000");
    }

    @Test
    void eurobond_detailNull_failSoft() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123CK49"));
        when(eurobondService.detail("US900123CK49")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "US900123CK49");

        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getLastPrice()).isNull();
    }
}
