package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link FundHoldingEnricher} eski
 * {@code PortfolioHoldingMarketEnricher.enrichFundHolding(...)} davranışıyla aynı.
 */
@ExtendWith(MockitoExtension.class)
class FundHoldingEnricherTest {

    @Mock RasyonetFundService rasyonetFundService;

    private FundHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FundHoldingEnricher(rasyonetFundService);
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    private static RasyonetFundDetailDto detailWithPrice(BigDecimal price, BigDecimal returnOneDay) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setPrice(price);
        d.setReturnOneDay(returnOneDay);
        d.setName("TEFAS Fonu");
        d.setCurrencyCode("TRY");
        return d;
    }

    private static RasyonetFundDto listed(String code, String sourceCode, BigDecimal price) {
        RasyonetFundDto d = new RasyonetFundDto();
        d.setCode(code);
        d.setSourceCode(sourceCode);
        d.setPrice(price);
        d.setName("Listed Fund");
        return d;
    }

    // ---- core paths --------------------------------------------------------

    @Test
    @DisplayName("enrich: zengin detay → mv (FUND_MONEY_SCALE=8); changePercent = returnOneDay")
    void enrich_richDetailPath() {
        // RasyonetFundLookup boş listelerle null döner
        // Default sources: TMF, TPF, TAF — ilkinde bulduk
        when(rasyonetFundService.getFundDetailRich(eq("ABC"), eq("TMF")))
                .thenReturn(detailWithPrice(new BigDecimal("12.50"), new BigDecimal("1.5")));

        PortfolioHoldingResponse h = holding("abc", new BigDecimal("100"), new BigDecimal("1000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("12.50");
        // 12.50 × 100 = 1250.00000000 (FUND_MONEY_SCALE=8)
        assertThat(h.getMarketValue()).isEqualByComparingTo("1250.00000000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("250.00000000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("TEFAS Fonu");
        assertThat(h.getChangePercent()).isEqualByComparingTo("1.5");
        // change = 12.50 × 1.5 / 100 = 0.1875
        assertThat(h.getChange()).isEqualByComparingTo("0.187500000000");
        assertThat(h.getReturnOneDay()).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("enrich: TMF fiyat 0 → TPF dene, TPF varsa onu kullan")
    void enrich_sourceFallback() {
        when(rasyonetFundService.getFundDetailRich("XYZ", "TMF")).thenReturn(null);
        when(rasyonetFundService.getFundDetailRich("XYZ", "TPF"))
                .thenReturn(detailWithPrice(new BigDecimal("5"), null));

        PortfolioHoldingResponse h = holding("XYZ", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("5");
        verify(rasyonetFundService).getFundDetailRich("XYZ", "TPF");
    }

    @Test
    @DisplayName("enrich: tüm rich kaynaklar boş → listed fallback (varsa)")
    void enrich_listFallback() {
        when(rasyonetFundService.getAllFunds())
                .thenReturn(List.of(listed("GZL", "TMF", new BigDecimal("8"))));
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GZL", new BigDecimal("10"), BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("8");
        assertThat(h.getName()).isEqualTo("Listed Fund");
    }

    @Test
    @DisplayName("enrich: hiçbir kaynak fiyat vermezse IllegalArgumentException")
    void enrich_noPriceAnywhere_throws() {
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("NONE", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fund price not found");
    }

    // ---- currency safety ---------------------------------------------------

    @Test
    @DisplayName("enrich: detail.currencyCode 'TMF' gibi geçersizse fallback TRY")
    void enrich_invalidCurrencyCode_fallbackToTry() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
        RasyonetFundDetailDto d = detailWithPrice(new BigDecimal("10"), null);
        d.setCurrencyCode("TMF");    // ISO 4217 değil
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(d);

        PortfolioHoldingResponse h = holding("ABC", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: USD currencyCode korunur (ISO 4217)")
    void enrich_validCurrencyCode_kept() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
        RasyonetFundDetailDto d = detailWithPrice(new BigDecimal("10"), null);
        d.setCurrencyCode("USD");
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(d);

        PortfolioHoldingResponse h = holding("USDFD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("enrich: returnOneDay null → change ve changePercent her ikisi de null")
    void enrich_noReturnOneDay_changeNull() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
        when(rasyonetFundService.getFundDetailRich(any(), any()))
                .thenReturn(detailWithPrice(new BigDecimal("10"), null));

        PortfolioHoldingResponse h = holding("ABC", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getChange()).isNull();
        assertThat(h.getChangePercent()).isNull();
    }

    // ---- price history → 52w + MA -----------------------------------------

    @Test
    @DisplayName("enrich: priceHistory varsa 52w high/low currentNav ile genişler; MA20/MA50 hesaplanır")
    void enrich_priceHistory_fills52wAndMa() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ph.add(new RasyonetFundDetailDto.PricePoint("2025-01-" + (i + 1), new BigDecimal(i + 1)));
        }
        RasyonetFundDetailDto d = detailWithPrice(new BigDecimal("100"), null);
        d.setPriceHistory(ph);
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(d);

        PortfolioHoldingResponse h = holding("ABC", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // closes = 1..20, currentNav = 100 → hi = max(20, 100) = 100; lo = min(1, 100) = 1
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("100");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        // forMa = closes + currentNav (eklenir) → 21 değer; MA20 = ortalama(son 20)
        assertThat(h.getMa20()).isNotNull();
    }
}
