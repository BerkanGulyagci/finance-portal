package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * FX watchlist item zenginleştirmesi: TCMB son kur (alış/satış) + 1Y kapanışlardan 52w/MA.
 * JPY gibi unit>1 olan kotelerde her şey 1-birim'e normalize edilir.
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichFx + fxCloses1y} idi;
 * davranış aynen taşındı (characterization-test-driven extraction).
 */
@Component
public class FxWatchlistEnricher {

    private final MarketFxService marketFxService;

    public FxWatchlistEnricher(MarketFxService marketFxService) {
        this.marketFxService = marketFxService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        FxLatestRates fx = marketFxService.getTcmbLatestRates(sym);
        FxRateItem rate = fx.getRates().stream()
                .filter(x -> sym.equalsIgnoreCase(x.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("FX rate not found: " + sym));

        int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
        BigDecimal buy = rate.getBuy();
        BigDecimal sell = rate.getSell();
        if (unit > 1) {
            BigDecimal u = BigDecimal.valueOf(unit);
            if (buy != null) {
                buy = buy.divide(u, 6, RoundingMode.HALF_UP);
            }
            if (sell != null) {
                sell = sell.divide(u, 6, RoundingMode.HALF_UP);
            }
        }

        r.setBuy(buy);
        r.setSell(sell);
        r.setLastPrice(sell);
        r.setCurrency("TRY");
        r.setAsOf(PortfolioDateTimeParse.parseLenient(fx.getAsOf()));

        applyMaAnd52w(r, closes1y(sym, unit));
    }

    /** 1Y kapanış serisi — unit'e göre normalize edilmiş. */
    private List<BigDecimal> closes1y(String symbol, int unit) {
        try {
            FxHistory hist = marketFxService.getFxHistory(symbol, "1Y");
            if (hist == null || hist.getPoints() == null) {
                return null;
            }
            return hist.getPoints().stream()
                    .map(FxHistoryPoint::getClose)
                    .filter(Objects::nonNull)
                    .map(c -> unit > 1 ? c.divide(BigDecimal.valueOf(unit), 6, RoundingMode.HALF_UP) : c)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }
}
