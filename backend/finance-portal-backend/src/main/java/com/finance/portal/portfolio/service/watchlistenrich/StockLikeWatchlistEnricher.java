package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * Stock benzeri (BIST hissesi veya Yahoo vadelisi ES=F vb.) watchlist item zenginleştirmesi.
 * StockQueryService.getStockSummary spot fiyat + 1y chart trend metrikleri.
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichStockLike + applyStockSummary +
 * stockCloses1y} idi; davranış aynen taşındı.
 */
@Component
public class StockLikeWatchlistEnricher {

    private final StockQueryService stockQueryService;

    public StockLikeWatchlistEnricher(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        String upper = symbol.toUpperCase(Locale.ROOT);
        StockSummary s = stockQueryService.getStockSummary(upper);
        applySummary(r, s);
        applyMaAnd52w(r, closes1y(upper));
    }

    private static void applySummary(WatchlistItemResponse r, StockSummary s) {
        r.setLastPrice(s.getPrice());
        r.setCurrency(s.getCurrency());
        r.setHigh(s.getDayHigh());
        r.setLow(s.getDayLow());
        r.setChange(s.getChange());
        r.setChangePercent(s.getChangePercent());
        r.setVolume(s.getVolume());
        r.setAsOf(PortfolioDateTimeParse.parseLenient(s.getAsOf()));

        if (s.getPrice() != null && s.getChange() != null) {
            r.setOpen(s.getPrice().subtract(s.getChange()));
        }
    }

    /** 1Y günlük chart kapanışları. Symbol upper-case verilmeli. */
    List<BigDecimal> closes1y(String upperSymbol) {
        try {
            StockChartResponse chart = stockQueryService.getStockChartWithParams(upperSymbol, "1y", "1d");
            return chart != null ? chart.getClosePrices() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
