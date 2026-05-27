package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * CRYPTO watchlist item zenginleştirmesi: anlık fiyat + 24h değişim + hacim +
 * 52w/MA20/MA50 (CoinGecko 1y kapanışları).
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichCrypto + cryptoCloses1y} idi;
 * davranış aynen taşındı (characterization-test-driven extraction).
 */
@Component
public class CryptoWatchlistEnricher {

    private final CryptoMarketService cryptoMarketService;

    public CryptoWatchlistEnricher(CryptoMarketService cryptoMarketService) {
        this.cryptoMarketService = cryptoMarketService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        CryptoMarketItem item = cryptoMarketService.findBySymbol(symbol);
        r.setLastPrice(item.getCurrentPrice());
        r.setCurrency("TRY");
        r.setHigh(item.getHigh24h());
        r.setLow(item.getLow24h());
        r.setChange(item.getPriceChange24h());
        r.setChangePercent(item.getPriceChangePercentage24h());
        r.setVolume(item.getTotalVolume() != null ? item.getTotalVolume().longValue() : null);
        r.setAsOf(PortfolioDateTimeParse.parseLenient(item.getLastUpdated()));

        if (item.getCurrentPrice() != null && item.getPriceChange24h() != null) {
            r.setOpen(item.getCurrentPrice().subtract(item.getPriceChange24h()));
        }

        // Trend için 7 günlük momentum + ~1y kapanışlardan MA/52w (CRYPTO için applyTrendSignals'ta atlanır,
        // bu yüzden burada doldurulur).
        r.setPriceChangePercentage7d(item.getPriceChangePercentage7d());
        applyMaAnd52w(r, closes1y(item.getId()));
    }

    /** Coingecko market_chart (1y TRY) kapanışları. */
    private List<BigDecimal> closes1y(String coinId) {
        if (coinId == null || coinId.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> chart = cryptoMarketService.getMarketChart(coinId, "365", "try", null, null);
            if (chart == null || !(chart.get("prices") instanceof List<?> rows)) {
                return null;
            }
            List<BigDecimal> closes = new ArrayList<>(rows.size());
            for (Object o : rows) {
                if (o instanceof List<?> row && row.size() >= 2 && row.get(1) instanceof Number n) {
                    closes.add(BigDecimal.valueOf(n.doubleValue()));
                }
            }
            return closes;
        } catch (Exception e) {
            return null;
        }
    }
}
