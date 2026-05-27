package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link CommodityHoldingEnricher} eski
 * {@code PortfolioHoldingMarketEnricher.enrichCommodityHolding(...)} + {@code enrichSilverHolding(...)}
 * davranışıyla aynı kalmalı. Symbol içinde ":" varsa → SILVER branch'i, yoksa Yahoo emtia.
 */
@ExtendWith(MockitoExtension.class)
class CommodityHoldingEnricherTest {

    @Mock SilverMarketService silverMarketService;
    @Mock YahooCommodityService yahooCommodityService;
    @Mock MarketFxService marketFxService;

    private CommodityHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new CommodityHoldingEnricher(silverMarketService, yahooCommodityService, marketFxService);
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    // =========================================================================
    // Yahoo commodity branch (no ":")
    // =========================================================================

    @Test
    @DisplayName("Yahoo emtia: displayPrice → mv/pl, name, change, 52w spot'tan kopyalanır")
    void yahoo_corePath() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("85"));
        spot.setChange(new BigDecimal("1.5"));
        spot.setChangePercent(new BigDecimal("1.8"));
        spot.setDayHigh(new BigDecimal("90"));
        spot.setDayLow(new BigDecimal("80"));
        spot.setWeekHigh52(new BigDecimal("120"));
        spot.setWeekLow52(new BigDecimal("60"));
        spot.setDisplayNameTr("Doğal Gaz");
        spot.setLastUpdated("2026-05-26T17:00:00");
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("NG=F", new BigDecimal("10"), new BigDecimal("700"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("85");
        assertThat(h.getMarketValue()).isEqualByComparingTo("850.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("150.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Doğal Gaz");
        assertThat(h.getChange()).isEqualByComparingTo("1.5");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("120");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("Yahoo emtia: displayPrice yoksa rawPrice kullanılır")
    void yahoo_rawPriceFallback() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setRawPrice(new BigDecimal("100"));
        when(yahooCommodityService.getSpot(any())).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("CL=F", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Yahoo emtia: hem displayPrice hem rawPrice null → IllegalStateException")
    void yahoo_noPrice_throws() {
        when(yahooCommodityService.getSpot("X")).thenReturn(new CommoditySpotDto());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Commodity price unavailable");
    }

    @Test
    @DisplayName("Yahoo emtia: displayNameTr yoksa displayNameEn fallback")
    void yahoo_nameFallback() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("1"));
        spot.setDisplayNameEn("Natural Gas");
        when(yahooCommodityService.getSpot(any())).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("NG=F", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getName()).isEqualTo("Natural Gas");
    }

    // =========================================================================
    // Silver branch (":" splitting)
    // =========================================================================

    @Test
    @DisplayName("SILVER:KG_TRY: weightedAverageTryKg → fiyat; name = Gümüş; currency = TRY")
    void silverKg_corePath() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setWeightedAverageTryKg(new BigDecimal("100000"));
        spot.setHighTryKg(new BigDecimal("105000"));
        spot.setLowTryKg(new BigDecimal("95000"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("SILVER:KG_TRY", new BigDecimal("2"), new BigDecimal("180000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100000");
        assertThat(h.getMarketValue()).isEqualByComparingTo("200000.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("20000.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Gümüş");
        assertThat(h.getDayHigh()).isEqualByComparingTo("105000");
    }

    @Test
    @DisplayName("SILVER:KG_TRY: fiyat null → IllegalStateException")
    void silver_noPrice_throws() {
        when(silverMarketService.getSpotSilver()).thenReturn(new SilverSpotResponse());

        PortfolioHoldingResponse h = holding("SILVER:KG_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Silver price unavailable");
    }

    @Test
    @DisplayName("SILVER:BOGUS → UnsupportedOperationException")
    void silver_unknownCat_throws() {
        when(silverMarketService.getSpotSilver()).thenReturn(new SilverSpotResponse());

        PortfolioHoldingResponse h = holding("SILVER:BOGUS", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported silver category");
    }

    @Test
    @DisplayName("Dispatch: ':' yoksa Yahoo branch (silver mock'ları kullanılmaz)")
    void dispatch_noColon_goesToYahoo() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("50"));
        when(yahooCommodityService.getSpot("CL=F")).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("CL=F", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("50");
    }
}
