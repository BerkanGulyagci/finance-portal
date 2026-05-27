package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxWatchlistEnricherTest {

    @Mock MarketFxService marketFxService;

    private FxWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FxWatchlistEnricher(marketFxService);
    }

    private static FxLatestRates latest(String sym, BigDecimal sell, BigDecimal buy, int unit) {
        return new FxLatestRates("TCMB", "interbank", "TRY", "2026-05-26",
                List.of(new FxRateItem(sym, buy, sell, unit)));
    }

    private static FxHistory history(BigDecimal... closes) {
        List<FxHistoryPoint> pts = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            pts.add(new FxHistoryPoint("2025-01-" + (i + 1), closes[i]));
        }
        FxHistory h = new FxHistory();
        h.setPoints(pts);
        return h;
    }

    @Test
    @DisplayName("enrich: buy/sell/lastPrice + currency + asOf doldurulur")
    void enrich_corePath() {
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(latest("USD", new BigDecimal("35.5"), new BigDecimal("35.4"), 1));
        when(marketFxService.getFxHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "usd");

        assertThat(r.getBuy()).isEqualByComparingTo("35.4");
        assertThat(r.getSell()).isEqualByComparingTo("35.5");
        assertThat(r.getLastPrice()).isEqualByComparingTo("35.5");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: unit>1 (JPY) → buy/sell unit'e bölünür")
    void enrich_unitGreaterThanOne_divides() {
        when(marketFxService.getTcmbLatestRates("JPY"))
                .thenReturn(latest("JPY", new BigDecimal("23.45"), new BigDecimal("23.30"), 100));
        when(marketFxService.getFxHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "JPY");

        assertThat(r.getSell()).isEqualByComparingTo("0.234500");
        assertThat(r.getBuy()).isEqualByComparingTo("0.233000");
    }

    @Test
    @DisplayName("enrich: latest'te symbol yoksa IllegalArgumentException")
    void enrich_symbolNotFound_throws() {
        when(marketFxService.getTcmbLatestRates("XXX"))
                .thenReturn(new FxLatestRates("TCMB", "interbank", "TRY", "now", List.of()));

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FX rate not found");
    }

    @Test
    @DisplayName("enrich: history varsa 52w high/low + MA20")
    void enrich_history_trendMetrics() {
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(latest("USD", new BigDecimal("35"), null, 1));
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(marketFxService.getFxHistory("USD", "1Y")).thenReturn(history(vals));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "USD");

        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: history fırlatırsa core fields doldurulmuş kalır")
    void enrich_historyThrows_isSwallowed() {
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(latest("USD", new BigDecimal("35"), null, 1));
        when(marketFxService.getFxHistory(any(), any())).thenThrow(new RuntimeException("TCMB down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "USD");

        assertThat(r.getSell()).isEqualByComparingTo("35");
        assertThat(r.getMa20()).isNull();
    }
}
