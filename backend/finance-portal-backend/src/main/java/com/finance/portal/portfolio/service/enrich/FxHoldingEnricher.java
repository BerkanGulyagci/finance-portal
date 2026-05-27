package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.MONEY_SCALE;

/**
 * FX holding zenginleştirmesi (USD, EUR, JPY vb.). Kaynak: {@link MarketFxService}.
 *
 * <ul>
 *   <li><b>Anlık fiyat:</b> TCMB satış kuru (kullanıcı perspektifi); satış yoksa alış.</li>
 *   <li><b>Birim:</b> JPY gibi 100'lük kotelerde her şey unit ile bölünür (1 birim cinsi).</li>
 *   <li><b>Günlük değişim:</b> son iki tarihsel kapanışın farkı / önceki * 100.</li>
 *   <li><b>52 hafta:</b> 1Y kapanışlardan min/max.</li>
 *   <li><b>1M / 3M getiri:</b> yaklaşık 22 / 66 işlem günü öncesi kapanışa göre yüzde.</li>
 *   <li><b>MA20/MA50:</b> son N kapanış üzerinden basit hareketli ortalama.</li>
 * </ul>
 *
 * <p>Davranış {@code PortfolioHoldingMarketEnricher.enrichFxHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class FxHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(FxHoldingEnricher.class);

    private final MarketFxService marketFxService;

    public FxHoldingEnricher(MarketFxService marketFxService) {
        this.marketFxService = marketFxService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol() != null
                ? holding.getSymbol().toUpperCase(Locale.ROOT)
                : "";

        // 1) Anlık kur — kullanıcı perspektifinde satış kuru (BUY referansı)
        FxLatestRates latest = marketFxService.getTcmbLatestRates(symbol);
        FxRateItem rate = latest.getRates().stream()
                .filter(r -> symbol.equalsIgnoreCase(r.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("FX rate not found for: " + symbol));

        int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
        BigDecimal unitBd = BigDecimal.valueOf(unit);
        BigDecimal currentPrice = rate.getSell();
        if (currentPrice == null) {
            currentPrice = rate.getBuy();
        }
        if (currentPrice == null) {
            throw new IllegalStateException("FX price unavailable for: " + symbol);
        }
        if (unit > 1) {
            currentPrice = currentPrice.divide(unitBd, 6, RoundingMode.HALF_UP);
        }

        BigDecimal mv = currentPrice.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(currentPrice);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(latest.getAsOf()));
        holding.setName(symbol + "/TRY");

        // 2) Tarihsel veriden günlük değişim, 52w aralığı, dönemsel getiriler, MA
        applyHistoryMetrics(holding, symbol, unit, unitBd);
    }

    private void applyHistoryMetrics(PortfolioHoldingResponse holding, String symbol,
                                     int unit, BigDecimal unitBd) {
        try {
            FxHistory hist = marketFxService.getFxHistory(symbol, "1Y");
            List<FxHistoryPoint> pts = hist != null ? hist.getPoints() : null;
            if (pts == null || pts.isEmpty()) {
                return;
            }
            List<BigDecimal> closes = pts.stream()
                    .filter(p -> p.getClose() != null)
                    .sorted(Comparator.comparing(FxHistoryPoint::getDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> {
                        BigDecimal c = p.getClose();
                        return unit > 1 ? c.divide(unitBd, 6, RoundingMode.HALF_UP) : c;
                    })
                    .collect(Collectors.toList());

            int n = closes.size();
            if (n >= 2) {
                BigDecimal last = closes.get(n - 1);
                BigDecimal prev = closes.get(n - 2);
                BigDecimal change = last.subtract(prev);
                holding.setChange(change.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                if (prev.compareTo(BigDecimal.ZERO) != 0) {
                    holding.setChangePercent(change
                            .divide(prev, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }

            holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
            holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));

            BigDecimal latestClose = closes.get(n - 1);
            holding.setReturnOneMonth(periodReturnPercent(closes, latestClose, 22));
            holding.setReturnThreeMonths(periodReturnPercent(closes, latestClose, 66));

            holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
            holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
        } catch (Exception e) {
            log.debug("FX history enrichment skipped for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Dizinin son elemanına göre {@code daysBack} işlem günü öncesinin yüzdesel değişimini hesaplar.
     * Yeterli veri yoksa null döner.
     */
    private static BigDecimal periodReturnPercent(List<BigDecimal> closes, BigDecimal latest, int daysBack) {
        if (closes == null || latest == null) {
            return null;
        }
        int n = closes.size();
        if (n <= daysBack) {
            return null;
        }
        BigDecimal past = closes.get(n - 1 - daysBack);
        if (past == null || past.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latest.subtract(past)
                .divide(past, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
