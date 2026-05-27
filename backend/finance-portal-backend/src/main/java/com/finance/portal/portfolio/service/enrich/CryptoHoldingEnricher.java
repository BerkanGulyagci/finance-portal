package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.applyMasFromCloses;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * Kripto holding zenginleştirmesi: anlık fiyat + günlük değişim + hacim + 52w + MA20/MA50.
 * Kaynak: {@link CryptoMarketService} (CoinGecko adapter, TRY cinsi).
 *
 * <p>Daha önce {@code PortfolioHoldingMarketEnricher.enrichCryptoHolding(...)} idi;
 * davranış aynen taşındı (characterization-test-driven extraction).
 */
@Component
public class CryptoHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(CryptoHoldingEnricher.class);

    private final CryptoMarketService cryptoMarketService;

    public CryptoHoldingEnricher(CryptoMarketService cryptoMarketService) {
        this.cryptoMarketService = cryptoMarketService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        CryptoMarketItem item = cryptoMarketService.findBySymbol(holding.getSymbol());

        BigDecimal price = item.getCurrentPrice();
        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(item.getLastUpdated()));
        holding.setName(item.getName());
        holding.setChange(item.getPriceChange24h());
        holding.setChangePercent(item.getPriceChangePercentage24h());
        holding.setPriceChangePercentage7d(item.getPriceChangePercentage7d());
        holding.setVolume(item.getTotalVolume() != null ? item.getTotalVolume().longValue() : null);
        holding.setDayHigh(item.getHigh24h());
        holding.setDayLow(item.getLow24h());
        holding.setMarketCap(item.getMarketCap());

        // 52 hafta aralığı + MA20/MA50 — trend hesabının diğer türlerle aynı (çoklu-sinyal) çalışması için.
        // CoinGecko market_chart (~1y TRY, @Cacheable) kapanışlarından hesaplanır.
        applyCryptoHistoryMetrics(holding, item.getId());
    }

    /** Kripto ~1 yıl TRY kapanışlarından 52 hafta min/max ve MA20/MA50 (trend için). */
    private void applyCryptoHistoryMetrics(PortfolioHoldingResponse holding, String coinId) {
        if (coinId == null || coinId.isBlank()) {
            return;
        }
        try {
            Map<String, Object> chart = cryptoMarketService.getMarketChart(coinId, "365", "try", null, null);
            if (chart == null) {
                return;
            }
            Object raw = chart.get("prices");
            if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
                return;
            }
            List<BigDecimal> closes = new ArrayList<>(rows.size());
            for (Object rowObj : rows) {
                if (rowObj instanceof List<?> row && row.size() >= 2 && row.get(1) instanceof Number n) {
                    closes.add(BigDecimal.valueOf(n.doubleValue()));
                }
            }
            if (closes.isEmpty()) {
                return;
            }
            holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
            holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));
            applyMasFromCloses(holding, closes);
        } catch (Exception e) {
            log.debug("Crypto 52w/MA unavailable for {}: {}", coinId, e.getMessage());
        }
    }
}
