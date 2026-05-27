package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link FutureHoldingEnricher} VİOP holding'i için Akbank
 * kontrat listesi + İş Yatırım grafik kombinasyonunu eski davranışla aynı tutmalı.
 */
@ExtendWith(MockitoExtension.class)
class FutureHoldingEnricherTest {

    @Mock ViopService viopService;
    @Mock ViopChartService viopChartService;

    private FutureHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FutureHoldingEnricher(viopService, viopChartService);
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    private static ViopContractDetail detail(BigDecimal last, BigDecimal settlement,
                                             BigDecimal prevSettlement, String name) {
        ViopContractDetail d = new ViopContractDetail();
        d.setName(name);
        d.setLastPrice(last);
        d.setSettlementPrice(settlement);
        d.setPrevSettlementPrice(prevSettlement);
        d.setChangePercent(new BigDecimal("0.8"));
        d.setHigh(new BigDecimal("110"));
        d.setLow(new BigDecimal("95"));
        d.setTime("2026-05-26T17:30:00");
        return d;
    }

    /** dailyChartPoints: aynı günde her timestamp için (sırayla) listede son değer dailyCloses olur. */
    private static List<ViopChartPoint> dailyChart(int days) {
        List<ViopChartPoint> pts = new ArrayList<>();
        long ts = 1_700_000_000_000L;
        for (int i = 0; i < days; i++) {
            String dt = String.format("2025-%02d-%02d", 1 + i / 30, 1 + (i % 30));
            pts.add(new ViopChartPoint(ts, dt, new BigDecimal(i + 1)));
            ts += 24 * 60 * 60 * 1000L;
        }
        return pts;
    }

    // ---- core path ---------------------------------------------------------

    @Test
    @DisplayName("enrich: full enrich — fiyat = lastPrice; mv = qty × price × 1; pl = mv − cost")
    void enrich_fullPath() {
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(dailyChart(30));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_USDTRY0625")).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(detail(new BigDecimal("100"), new BigDecimal("99"),
                        new BigDecimal("98"), "F_USDTRY0625"));

        PortfolioHoldingResponse h = holding("F_USDTRY0625", new BigDecimal("3"), new BigDecimal("250"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
        // 3 × 100 × 1 = 300.0000
        assertThat(h.getMarketValue()).isEqualByComparingTo("300.0000");
        // 300 − 250 = 50.0000
        assertThat(h.getProfitLoss()).isEqualByComparingTo("50.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("F_USDTRY0625");
        assertThat(h.getChangePercent()).isEqualByComparingTo("0.8");
        assertThat(h.getDayHigh()).isEqualByComparingTo("110");
        assertThat(h.getDayLow()).isEqualByComparingTo("95");
        // change = current − prevSettlement = 100 − 98 = 2.0000
        assertThat(h.getChange()).isEqualByComparingTo("2.0000");
        // chart vardı → 52w doldu
        assertThat(h.getFiftyTwoWeekHigh()).isNotNull();
        assertThat(h.getFiftyTwoWeekLow()).isNotNull();
    }

    @Test
    @DisplayName("enrich: lastPrice yoksa settlementPrice fiyat olur")
    void enrich_lastPriceMissing_fallsBackToSettlement() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(detail(null, new BigDecimal("99"), null, "X"));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("99");
    }

    @Test
    @DisplayName("enrich: change sadece prevSettlement non-null ise hesaplanır")
    void enrich_changeOnlyWhenPrevSettlementPresent() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(detail(new BigDecimal("100"), null, null, "X"));   // prevSettlement null

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getChange()).isNull();
    }

    // ---- fallback / skip branches -----------------------------------------

    @Test
    @DisplayName("enrich: null/blank symbol → no-op (servisler çağrılmaz)")
    void enrich_blankSymbol_noOp() {
        PortfolioHoldingResponse h = holding("   ", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        verify(viopService, never()).findMatchingContract(any());
        verify(viopChartService, never()).getChart(any(), any());
        assertThat(h.getCurrentPrice()).isNull();
    }

    @Test
    @DisplayName("enrich: Akbank listesi eşleşmezse grafik 52w/MA yine doldurur ama mv/pl yok")
    void enrich_noContractMatch_chartStillRuns() {
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(dailyChart(30));
        when(viopService.findMatchingContract("X")).thenReturn(Optional.empty());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // grafik adımı geçtiği için 52w doldu
        assertThat(h.getFiftyTwoWeekHigh()).isNotNull();
        // ama detail yok → mv yok
        assertThat(h.getMarketValue()).isNull();
    }

    @Test
    @DisplayName("enrich: lastPrice + settlement her ikisi de null → fiyat dolmaz")
    void enrich_noPrice_returnsEarly() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c)).thenReturn(detail(null, null, null, "X"));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isNull();
        assertThat(h.getMarketValue()).isNull();
    }

    @Test
    @DisplayName("enrich: Akbank servisi exception fırlatırsa grafiği bozmaz")
    void enrich_listException_chartStillRuns() {
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(dailyChart(30));
        when(viopService.findMatchingContract(any())).thenThrow(new RuntimeException("Akbank down"));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // grafik enrichment'ı önce çalıştı, sonra liste patladı — 52w doldu
        assertThat(h.getFiftyTwoWeekHigh()).isNotNull();
        // detail elde edilemedi → mv yok
        assertThat(h.getMarketValue()).isNull();
    }

    @Test
    @DisplayName("enrich: kontrat adı kullanıcının yazdığından farklıysa grafik ikinci kez kanonik isimle denenir")
    void enrich_canonicalNameDifferent_chartCalledTwice() {
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(dailyChart(5));
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("user-typed")).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(detail(new BigDecimal("100"), null, null, "CANONICAL_NAME"));

        PortfolioHoldingResponse h = holding("user-typed", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        verify(viopChartService).getChart("user-typed", ViopChartPeriod.ONE_YEAR);
        verify(viopChartService).getChart("CANONICAL_NAME", ViopChartPeriod.ONE_YEAR);
    }

    @Test
    @DisplayName("enrich: chart 1Y boşsa 6A → 3A → 1A sırayla denenir")
    void enrich_chartPeriodFallbacks() {
        // 1Y, 6A, 3A boş; 1A dolu
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(List.of());
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.SIX_MONTHS))).thenReturn(List.of());
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.THREE_MONTHS))).thenReturn(List.of());
        when(viopChartService.getChart(any(), eq(ViopChartPeriod.ONE_MONTH))).thenReturn(dailyChart(20));
        when(viopService.findMatchingContract(any())).thenReturn(Optional.empty());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekHigh()).isNotNull();
        verify(viopChartService).getChart("X", ViopChartPeriod.ONE_MONTH);
    }
}
