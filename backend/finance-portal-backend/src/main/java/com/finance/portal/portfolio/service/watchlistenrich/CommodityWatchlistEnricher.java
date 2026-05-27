package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioHistoryPoints;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * COMMODITY watchlist item zenginleştirmesi: BIST gümüş (SILVER:*), platin/paladyum
 * (PLATINUM:*, PALLADIUM:*) ve diğer Yahoo emtiası (NG=F, CL=F vb.).
 *
 * <p>Symbol ":" içeriyorsa metal:cat split; aksi halde Yahoo spot. Davranış
 * {@code PortfolioWatchlistMarketEnricher.enrichCommodity + applyPreciousHighLowFromHistoryWindow
 * + commodityCloses1y + silverCloses1y} eski kodundan aynen taşındı.
 */
@Component
public class CommodityWatchlistEnricher {

    private final SilverMarketService silverMarketService;
    private final PreciousMetalService preciousMetalService;
    private final YahooCommodityService yahooCommodityService;

    public CommodityWatchlistEnricher(SilverMarketService silverMarketService,
                                      PreciousMetalService preciousMetalService,
                                      YahooCommodityService yahooCommodityService) {
        this.silverMarketService = silverMarketService;
        this.preciousMetalService = preciousMetalService;
        this.yahooCommodityService = yahooCommodityService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        if (symbol != null && symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal = parts[0].toUpperCase(Locale.ROOT);
            String cat   = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT) : "";

            if ("SILVER".equals(metal)) {
                enrichSilver(r, cat);
                applyMaAnd52w(r, silverCloses1y(cat));
                return;
            }
            if ("PLATINUM".equals(metal) || "PALLADIUM".equals(metal)) {
                enrichPrecious(r, "PLATINUM".equals(metal)
                        ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM, cat);
                // Precious metalde 1Y MA/52w trend serisi henüz uygulanmıyor (eski davranış aynı).
                return;
            }
            throw new UnsupportedOperationException("Unsupported precious metal symbol: " + symbol);
        }

        enrichYahoo(r, symbol);
        applyMaAnd52w(r, commodityCloses1y(symbol));
    }

    private void enrichYahoo(WatchlistItemResponse r, String symbol) {
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        r.setLastPrice(spot.getDisplayPrice());
        r.setCurrency(spot.getDisplayCurrency());
        r.setOpen(spot.getPreviousClose());
        r.setHigh(spot.getDayHigh());
        r.setLow(spot.getDayLow());
        r.setChange(spot.getChange());
        r.setChangePercent(spot.getChangePercent());
        // Emtia için güvenilir işlem hacmi gelmiyor → "Hacim" doldurulmaz.
        r.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
    }

    @SuppressWarnings("java:S3776")
    private void enrichSilver(WatchlistItemResponse r, String cat) {
        SilverSpotResponse spot = silverMarketService.getSpotSilver();
        BigDecimal price = null;
        String currency = null;
        BigDecimal high = null;
        BigDecimal low = null;
        Long volume = null;
        BigDecimal open = null;
        BigDecimal change = null;
        BigDecimal changePct = null;
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(spot.getLastUpdated());

        switch (cat) {
            case "GRAM_TRY" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null && lp.latest().getClose() != null) {
                    price = lp.latest().getClose();
                    open = lp.latest().getOpen();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                    volume = lp.latest().getVolume();
                } else {
                    price = spot.getSilverGramCloseTry();
                    high = spot.getSilverGramHighTry();
                    low = spot.getSilverGramLowTry();
                }
                if (price == null) {
                    price = spot.getSilverGramTry();
                }
                if (lp.latest() != null && lp.prev() != null && lp.latest().getClose() != null
                        && lp.prev().getClose() != null) {
                    BigDecimal refPrice = price != null ? price : lp.latest().getClose();
                    change = refPrice.subtract(lp.prev().getClose());
                    if (lp.prev().getClose().compareTo(BigDecimal.ZERO) != 0) {
                        changePct = change.divide(lp.prev().getClose(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                }
                currency = "TRY";
            }
            case "KG_TRY" -> {
                price = spot.getWeightedAverageTryKg();
                currency = "TRY";
                high = spot.getHighTryKg();
                low = spot.getLowTryKg();
                volume = spot.getVolumeTry() != null ? spot.getVolumeTry().longValue() : null;
                try {
                    SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                    PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                    if (lp.latest() != null && lp.latest().getCloseTryKg() != null) {
                        open = lp.latest().getOpen() != null && lp.prev() != null
                                ? lp.prev().getCloseTryKg()
                                : null;
                        if (lp.prev() != null && lp.prev().getCloseTryKg() != null
                                && lp.latest().getCloseTryKg() != null) {
                            change = lp.latest().getCloseTryKg().subtract(lp.prev().getCloseTryKg());
                            if (lp.prev().getCloseTryKg().compareTo(BigDecimal.ZERO) != 0) {
                                changePct = change.divide(lp.prev().getCloseTryKg(), 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 1W history fail → spot zaten doldurdu
                }
            }
            case "USD_ONS" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "USD");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null) {
                    price = lp.latest().getClose();
                    open = lp.latest().getOpen();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                    volume = lp.latest().getVolume();
                    if (price != null && open != null) {
                        change = price.subtract(open);
                        if (open.compareTo(BigDecimal.ZERO) != 0) {
                            changePct = change.divide(open, 6, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                } else {
                    price = spot.getSilverUsdOns();
                }
                currency = "USD";
            }
            default -> throw new UnsupportedOperationException("Unsupported silver category: " + cat);
        }

        r.setLastPrice(price);
        r.setOpen(open);
        r.setCurrency(currency);
        r.setHigh(high);
        r.setLow(low);
        r.setChange(change);
        r.setChangePercent(changePct);
        // Emtia (gümüş) için "Hacim" gösterilmez (güvenilir değil).
        r.setAsOf(asOf);
    }

    private void enrichPrecious(WatchlistItemResponse r, PreciousMetalType type, String cat) {
        PreciousMetalSpotResponse spot = preciousMetalService.getSpot(type);
        String histCurrency = switch (cat) {
            case "USD_ONS" -> "USD";
            case "EUR_ONS" -> "EUR";
            default -> "TRY";
        };
        PreciousMetalHistoryResponse hist = preciousMetalService.getHistory(type, "1W", histCurrency);

        BigDecimal priceVal;
        String currencyVal;
        switch (cat) {
            case "GRAM_TRY" -> { priceVal = spot.getTryGram(); currencyVal = "TRY"; }
            case "KG_TRY"   -> { priceVal = spot.getTryKg();   currencyVal = "TRY"; }
            case "USD_ONS"  -> { priceVal = spot.getUsdOns();  currencyVal = "USD"; }
            case "EUR_ONS"  -> { priceVal = spot.getEurOns();  currencyVal = "EUR"; }
            default -> throw new UnsupportedOperationException("Unsupported precious category: " + cat);
        }

        r.setLastPrice(priceVal);
        r.setCurrency(currencyVal);
        applyPreciousHighLowFromHistoryWindow(r, hist, cat, priceVal);

        try {
            PortfolioHistoryPoints.PreciousWindow pp = PortfolioHistoryPoints.preciousWindow(hist);
            if (pp.latest() != null && pp.prev() != null) {
                BigDecimal last = PortfolioHistoryPoints.preciousPointValue(pp.latest(), cat);
                BigDecimal prev = PortfolioHistoryPoints.preciousPointValue(pp.prev(), cat);
                if (last != null && prev != null) {
                    r.setOpen(prev);
                    BigDecimal ch = last.subtract(prev);
                    r.setChange(ch);
                    if (prev.compareTo(BigDecimal.ZERO) != 0) {
                        r.setChangePercent(ch.divide(prev, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                }
            }
        } catch (Exception ignored) {
            // Precious history fail → spot fields already set
        }

        r.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
    }

    private static void applyPreciousHighLowFromHistoryWindow(WatchlistItemResponse r,
            PreciousMetalHistoryResponse hist, String cat, BigDecimal spotPrice) {
        if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
            return;
        }
        var pts = hist.getPoints();
        int n = pts.size();
        int windowStart = Math.max(0, n - 5);
        BigDecimal maxV = null;
        BigDecimal minV = null;
        for (int i = windowStart; i < n; i++) {
            BigDecimal v = PortfolioHistoryPoints.preciousPointValue(pts.get(i), cat);
            if (v == null) {
                continue;
            }
            maxV = maxV == null ? v : maxV.max(v);
            minV = minV == null ? v : minV.min(v);
        }
        if (spotPrice != null) {
            maxV = maxV == null ? spotPrice : maxV.max(spotPrice);
            minV = minV == null ? spotPrice : minV.min(spotPrice);
        }
        if (maxV != null) {
            r.setHigh(maxV.setScale(4, RoundingMode.HALF_UP));
        }
        if (minV != null) {
            r.setLow(minV.setScale(4, RoundingMode.HALF_UP));
        }
    }

    private List<BigDecimal> commodityCloses1y(String symbol) {
        try {
            CommodityHistoryResponse hist = yahooCommodityService.getHistory(symbol, "1Y", "1d");
            if (hist == null || hist.getPoints() == null) {
                return null;
            }
            return hist.getPoints().stream()
                    .map(CommodityHistoryPointDto::getDisplayClose)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }

    private List<BigDecimal> silverCloses1y(String cat) {
        try {
            String cur = cat.contains("USD") ? "USD" : "TRY";
            SilverHistoryResponse hist = silverMarketService.getSilverHistory("1Y", cur);
            if (hist == null || hist.getPoints() == null) {
                return null;
            }
            return hist.getPoints().stream()
                    .map(SilverHistoryPoint::getClose)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }
}
