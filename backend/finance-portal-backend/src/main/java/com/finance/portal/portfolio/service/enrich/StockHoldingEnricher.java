package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * STOCK (BIST) holding zenginleştirmesi.
 * Kaynak: {@link StockQueryService} — {@code getStockDetail} @Cacheable; anlık özet
 * (price/change/volume/dayHigh/dayLow/52w) buradan, MA20/MA50 ise 3 aylık günlük
 * chart kapanışlarından hesaplanır.
 *
 * <p>Daha önce {@code PortfolioHoldingMarketEnricher.enrichStockOrFutureHolding(...)} idi;
 * davranış aynen taşındı (characterization-test-driven extraction).
 */
@Component
public class StockHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(StockHoldingEnricher.class);

    private final StockQueryService stockQueryService;

    public StockHoldingEnricher(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol().toUpperCase(Locale.ROOT);
        StockDetail detail = stockQueryService.getStockDetail(symbol);
        StockSummary summary = detail.getSummary();

        BigDecimal price = summary.getPrice();
        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(summary.getCurrency() != null ? summary.getCurrency() : detail.getCurrency());
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(summary.getAsOf()));
        holding.setName(detail.getName() != null ? detail.getName()
                : (summary.getName() != null ? summary.getName() : holding.getSymbol()));
        holding.setChange(summary.getChange());
        holding.setChangePercent(summary.getChangePercent());
        holding.setVolume(summary.getVolume());
        holding.setDayHigh(summary.getDayHigh());
        holding.setDayLow(summary.getDayLow());
        holding.setFiftyTwoWeekHigh(detail.getFiftyTwoWeekHigh());
        holding.setFiftyTwoWeekLow(detail.getFiftyTwoWeekLow());

        applyMaFromThreeMonthChart(holding, symbol);
    }

    /** MA20 / MA50 — 3 aylık günlük kapanışlardan hesapla (önbellekte ise bedava). */
    private void applyMaFromThreeMonthChart(PortfolioHoldingResponse holding, String symbol) {
        try {
            StockChartResponse chart = stockQueryService.getStockChartWithParams(symbol, "3mo", "1d");
            List<BigDecimal> closes = chart.getClosePrices();
            holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
            holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
        } catch (Exception e) {
            log.debug("MA computation skipped for {}: {}", symbol, e.getMessage());
        }
    }
}
