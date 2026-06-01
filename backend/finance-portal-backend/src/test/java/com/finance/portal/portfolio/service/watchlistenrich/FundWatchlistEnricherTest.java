package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link FundWatchlistEnricher} eski
 * {@code PortfolioWatchlistMarketEnricher.enrichFund + applyRasyonetFundDetail +
 * applyRasyonetFundListRow + mapFundMetadata* + applyFundOhlcFromRasyonetHistory +
 * fillFundChangeFromDailyPercent} ile aynı davranmalı.
 */
@ExtendWith(MockitoExtension.class)
class FundWatchlistEnricherTest {

    @Mock RasyonetFundService rasyonetFundService;

    private FundWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FundWatchlistEnricher(rasyonetFundService);
    }

    /** Tüm liste lookup'larını boş döndür (kod listede yok varsayımı). */
    private void stubEmptyLists() {
        lenient().when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        lenient().when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        lenient().when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
    }

    private static RasyonetFundDto listedFund(String code, BigDecimal price) {
        RasyonetFundDto f = new RasyonetFundDto();
        f.setCode(code);
        f.setName("Test Fonu");
        f.setFundType("Hisse Senedi");
        f.setPrice(price);
        f.setReturnOneDay(new BigDecimal("1.5"));
        f.setReturnOneMonth(new BigDecimal("5"));
        f.setReturnThreeMonths(new BigDecimal("12"));
        f.setReturnYearToDate(new BigDecimal("20"));
        f.setReturnOneYear(new BigDecimal("40"));
        f.setRiskLevel(6);
        return f;
    }

    private static RasyonetFundDetailDto detail(BigDecimal price) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setName("Detay Fonu");
        d.setFundType("Karma");
        d.setCurrencyCode("TRY");
        d.setPrice(price);
        d.setReturnOneDay(new BigDecimal("2"));
        d.setReturnOneMonth(new BigDecimal("6"));
        d.setReturnThreeMonths(new BigDecimal("13"));
        d.setReturnYearToDate(new BigDecimal("21"));
        d.setReturnOneYear(new BigDecimal("41"));
        d.setRiskLevel(5);
        return d;
    }

    private static List<RasyonetFundDetailDto.PricePoint> priceHistory(BigDecimal... prices) {
        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            ph.add(new RasyonetFundDetailDto.PricePoint("2025-01-" + (i + 1), prices[i]));
        }
        return ph;
    }

    // ── Detail-path (rich detail price>0) ──────────────────────────────────────

    @Test
    @DisplayName("enrich: detay (TMF) fiyat>0 → NAV/currency/metadata + priceHistory OHLC")
    void enrich_detailPath_withPriceHistory() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("12.50"));
        d.setPriceHistory(priceHistory(
                new BigDecimal("12.00"), new BigDecimal("12.30"), new BigDecimal("12.50")));
        when(rasyonetFundService.getFundDetailRich("AFA", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "afa");

        assertThat(r.getLastPrice()).isEqualByComparingTo("12.50");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getFundName()).isEqualTo("Detay Fonu");
        assertThat(r.getFundType()).isEqualTo("Karma");
        assertThat(r.getFundReturnOneMonth()).isEqualByComparingTo("6");
        assertThat(r.getFundReturnOneYear()).isEqualByComparingTo("41");
        assertThat(r.getFundRiskLevel()).isEqualTo(5);
        // OHLC window: high=12.50, low=12.00; prevClose=12.30 → open=12.30, change=0.20
        assertThat(r.getHigh()).isEqualByComparingTo("12.50");
        assertThat(r.getLow()).isEqualByComparingTo("12.00");
        assertThat(r.getOpen()).isEqualByComparingTo("12.30");
        assertThat(r.getChange()).isEqualByComparingTo("0.20");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: detay priceHistory yok → returnOneDay% ile günlük değişim")
    void enrich_detailPath_noHistory_dailyPercent() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("10.20"));
        d.setPriceHistory(null);
        when(rasyonetFundService.getFundDetailRich("XYZ", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "XYZ");

        assertThat(r.getLastPrice()).isEqualByComparingTo("10.20");
        // changePercent = returnOneDay = 2.00
        assertThat(r.getChangePercent()).isEqualByComparingTo("2.00");
        // open = price - change; change derived from %; open ≈ impliedPrev
        assertThat(r.getOpen()).isNotNull();
        assertThat(r.getChange()).isNotNull();
    }

    @Test
    @DisplayName("enrich: currencyCode boşsa TRY varsayılır")
    void enrich_detailPath_blankCurrency_defaultsTry() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("9.00"));
        d.setCurrencyCode("  ");
        when(rasyonetFundService.getFundDetailRich("ABC", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "ABC");

        assertThat(r.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: listed sourceCode öncelikli source-code denenir")
    void enrich_listedSourceCode_isTriedFirst() {
        RasyonetFundDto listed = listedFund("GPB", new BigDecimal("5"));
        listed.setSourceCode("tpf"); // küçük harf → upper TPF normalize
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));

        RasyonetFundDetailDto d = detail(new BigDecimal("7.77"));
        when(rasyonetFundService.getFundDetailRich("GPB", "TPF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GPB");

        assertThat(r.getLastPrice()).isEqualByComparingTo("7.77");
        // TPF ilk denenmeli; TMF default'a düşmeden bulundu
        verify(rasyonetFundService).getFundDetailRich("GPB", "TPF");
    }

    @Test
    @DisplayName("enrich: TMF/TPF null/fiyatsız → TAF fallback denenir")
    void enrich_fallsThroughToTaf() {
        stubEmptyLists();
        when(rasyonetFundService.getFundDetailRich("FFF", "TMF")).thenReturn(null);
        // TPF: fiyat 0 → atlanır
        RasyonetFundDetailDto zero = detail(BigDecimal.ZERO);
        when(rasyonetFundService.getFundDetailRich("FFF", "TPF")).thenReturn(zero);
        when(rasyonetFundService.getFundDetailRich("FFF", "TAF"))
                .thenReturn(detail(new BigDecimal("3.33")));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "FFF");

        assertThat(r.getLastPrice()).isEqualByComparingTo("3.33");
        verify(rasyonetFundService).getFundDetailRich("FFF", "TAF");
    }

    // ── List-row fallback ──────────────────────────────────────────────────────

    @Test
    @DisplayName("enrich: detay yok ama listed fiyat>0 → list-row fallback")
    void enrich_listRowFallback() {
        RasyonetFundDto listed = listedFund("LST", new BigDecimal("8.40"));
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));
        // tüm detail kaynakları null
        when(rasyonetFundService.getFundDetailRich(eq("LST"), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "LST");

        assertThat(r.getLastPrice()).isEqualByComparingTo("8.40");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getFundName()).isEqualTo("Test Fonu");
        assertThat(r.getFundType()).isEqualTo("Hisse Senedi");
        assertThat(r.getFundReturnOneYear()).isEqualByComparingTo("40");
        assertThat(r.getFundRiskLevel()).isEqualTo(6);
        // returnOneDay = 1.5 → changePercent
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.50");
        assertThat(r.getAsOf()).isNotNull();
    }

    // ── Not found ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enrich: hiçbir kaynakta fiyat yok → IllegalArgumentException")
    void enrich_notFound_throws() {
        stubEmptyLists();
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "NONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fund price not found");
    }

    @Test
    @DisplayName("enrich: listed fiyat 0 ve detay yok → IllegalArgumentException")
    void enrich_listedZeroPrice_throws() {
        RasyonetFundDto listed = listedFund("ZRO", BigDecimal.ZERO);
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));
        when(rasyonetFundService.getFundDetailRich(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "ZRO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
