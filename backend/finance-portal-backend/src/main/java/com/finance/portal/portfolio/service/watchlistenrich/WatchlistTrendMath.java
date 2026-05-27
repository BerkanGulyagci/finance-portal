package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Watchlist item trend metrikleri için ortak yardımcı. Asset-type başına ayrı
 * {@code *WatchlistEnricher} sınıflarına bölünmüş olan
 * {@link com.finance.portal.portfolio.service.PortfolioWatchlistMarketEnricher}
 * orkestratörü ve alt enricher'ların paylaşılan logic'i: 52w high/low + MA20 + MA50.
 */
public final class WatchlistTrendMath {

    private WatchlistTrendMath() {
        // utility
    }

    /**
     * Verilen kapanış serisi üzerinden 52 hafta min/max ve MA20/MA50 hesaplar.
     * Liste null veya 2'den az eleman içeriyorsa no-op.
     */
    public static void applyMaAnd52w(WatchlistItemResponse r, List<BigDecimal> closes) {
        if (closes == null || closes.size() < 2) {
            return;
        }
        r.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
        r.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));
        r.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
        r.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
    }
}
