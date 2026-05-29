package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioHistoryPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.applyMasFromCloses;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * COMMODITY holding zenginleştirmesi. İki tür kaynak var:
 * <ul>
 *   <li><b>SILVER:GRAM_TRY</b> / <b>SILVER:KG_TRY</b> / <b>SILVER:USD_ONS</b> — BIST gümüş;
 *       {@link SilverMarketService} (BigPara spot + 1W/1Y history).</li>
 *   <li>Diğer her şey (NG=F, CL=F, GC=F vb.) — {@link YahooCommodityService} (Yahoo Finance).</li>
 * </ul>
 *
 * <p>Branch kararı symbol'da {@code ":"} olup olmamasına göre verilir. Davranış
 * {@code PortfolioHoldingMarketEnricher.enrichCommodityHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class CommodityHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(CommodityHoldingEnricher.class);

    private final SilverMarketService silverMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final MarketFxService marketFxService;
    private final PreciousMetalService preciousMetalService;

    public CommodityHoldingEnricher(SilverMarketService silverMarketService,
                                    YahooCommodityService yahooCommodityService,
                                    MarketFxService marketFxService,
                                    PreciousMetalService preciousMetalService) {
        this.silverMarketService = silverMarketService;
        this.yahooCommodityService = yahooCommodityService;
        this.marketFxService = marketFxService;
        this.preciousMetalService = preciousMetalService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol();
        if (symbol != null && symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal = parts[0].toUpperCase(Locale.ROOT);
            String cat   = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT) : "";
            if ("SILVER".equals(metal)) {
                enrichSilver(holding, cat);
                return;
            }
            if ("PLATINUM".equals(metal) || "PALLADIUM".equals(metal)) {
                PreciousMetalType type = "PLATINUM".equals(metal)
                        ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM;
                enrichPrecious(holding, type, cat);
                return;
            }
        }
        enrichYahoo(holding, symbol);
    }

    private void enrichYahoo(PortfolioHoldingResponse holding, String symbol) {
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        BigDecimal price = spot.getDisplayPrice() != null ? spot.getDisplayPrice() : spot.getRawPrice();
        if (price == null) {
            throw new IllegalStateException("Commodity price unavailable: " + symbol);
        }

        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        String displayName = spot.getDisplayNameTr() != null ? spot.getDisplayNameTr() : spot.getDisplayNameEn();
        if (displayName != null) {
            holding.setName(displayName);
        }
        holding.setChange(spot.getChange());
        holding.setChangePercent(spot.getChangePercent());
        // Emtia için güvenilir işlem hacmi gelmiyor → "Hacim" doldurulmaz.
        holding.setDayHigh(spot.getDayHigh());
        holding.setDayLow(spot.getDayLow());
        holding.setFiftyTwoWeekHigh(spot.getWeekHigh52());
        holding.setFiftyTwoWeekLow(spot.getWeekLow52());
        applyYahooHistoryMa(holding, symbol);
    }

    private void applyYahooHistoryMa(PortfolioHoldingResponse holding, String symbol) {
        try {
            CommodityHistoryResponse hist = yahooCommodityService.getHistory(symbol, "1Y", "1d");
            if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
                return;
            }
            BigDecimal usdTry = "TRY".equalsIgnoreCase(holding.getCurrency()) ? fetchUsdTryRate() : null;
            List<BigDecimal> closes = hist.getPoints().stream()
                    .map(CommodityHistoryPointDto::getDisplayClose)
                    .filter(Objects::nonNull)
                    .map(c -> usdTry != null ? c.multiply(usdTry).setScale(2, RoundingMode.HALF_UP) : c)
                    .collect(Collectors.toList());
            applyMasFromCloses(holding, closes);
        } catch (Exception e) {
            log.debug("Commodity MA skipped for {}: {}", symbol, e.getMessage());
        }
    }

    /** SILVER:GRAM_TRY / SILVER:KG_TRY / SILVER:USD_ONS gibi BIST gümüş sembolleri. */
    private void enrichSilver(PortfolioHoldingResponse holding, String cat) {
        SilverSpotResponse spot = silverMarketService.getSpotSilver();
        BigDecimal price = null;
        String currency = "TRY";
        BigDecimal high = null;
        BigDecimal low  = null;
        BigDecimal change = null;
        BigDecimal changePercent = null;

        switch (cat.isBlank() ? "GRAM_TRY" : cat) {
            case "GRAM_TRY" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null && lp.latest().getClose() != null) {
                    price = lp.latest().getClose();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                } else {
                    price = spot.getSilverGramCloseTry();
                    high = spot.getSilverGramHighTry();
                    low = spot.getSilverGramLowTry();
                }
                if (price == null) {
                    price = spot.getSilverGramTry();
                }
                if (lp.latest() != null && lp.prev() != null && lp.latest().getClose() != null
                        && lp.prev().getClose() != null && lp.prev().getClose().compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal refPrice = price != null ? price : lp.latest().getClose();
                    change = refPrice.subtract(lp.prev().getClose());
                    changePercent = change.divide(lp.prev().getClose(), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "KG_TRY" -> {
                price = spot.getWeightedAverageTryKg();
                high = spot.getHighTryKg();
                low = spot.getLowTryKg();
            }
            case "USD_ONS" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "USD");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null) {
                    price = lp.latest().getClose();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                    if (price != null && lp.prev() != null && lp.prev().getClose() != null
                            && lp.prev().getClose().compareTo(BigDecimal.ZERO) != 0) {
                        change = price.subtract(lp.prev().getClose());
                        changePercent = change.divide(lp.prev().getClose(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    price = spot.getSilverUsdOns();
                    currency = "USD";
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported silver category: " + cat);
        }

        if (price == null) {
            throw new IllegalStateException("Silver price unavailable for cat: " + cat);
        }

        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(currency);
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        holding.setName("Gümüş");
        holding.setChange(change);
        holding.setChangePercent(changePercent);
        holding.setDayHigh(high);
        holding.setDayLow(low);

        // 52-week range — 1Y history min/max + MA
        try {
            String histCurrency = "USD".equals(currency) ? "USD" : "TRY";
            SilverHistoryResponse hist1y = silverMarketService.getSilverHistory("1Y", histCurrency);
            if (hist1y != null && hist1y.getPoints() != null) {
                List<BigDecimal> closes1y = hist1y.getPoints().stream()
                        .map(SilverHistoryPoint::getClose)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!closes1y.isEmpty()) {
                    holding.setFiftyTwoWeekHigh(closes1y.stream().max(BigDecimal::compareTo).orElse(null));
                    holding.setFiftyTwoWeekLow(closes1y.stream().min(BigDecimal::compareTo).orElse(null));
                    applyMasFromCloses(holding, closes1y);
                }
            }
        } catch (Exception e) {
            log.debug("52w range unavailable for SILVER {}: {}", cat, e.getMessage());
        }
        // Emtia (gümüş) için "Hacim" gösterilmez (güvenilir işlem hacmi değil).
    }

    /**
     * BIST platin / paladyum: PLATINUM:GRAM_TRY / PLATINUM:KG_TRY / PLATINUM:USD_ONS / PLATINUM:EUR_ONS
     * (PALLADIUM: aynı katmanlar). Kaynak: PreciousMetalService (BIST metal-fiyatlari.php).
     * Watchlist enricher'ı ile aynı veri yolu; holding'e ek olarak 52w + MA20/MA50 uygular.
     */
    private void enrichPrecious(PortfolioHoldingResponse holding, PreciousMetalType type, String catRaw) {
        String cat = catRaw == null || catRaw.isBlank() ? "GRAM_TRY" : catRaw;
        PreciousMetalSpotResponse spot = preciousMetalService.getSpot(type);

        BigDecimal price;
        String currency;
        switch (cat) {
            case "GRAM_TRY" -> { price = spot.getTryGram(); currency = "TRY"; }
            case "KG_TRY"   -> { price = spot.getTryKg();   currency = "TRY"; }
            case "USD_ONS"  -> { price = spot.getUsdOns();  currency = "USD"; }
            case "EUR_ONS"  -> { price = spot.getEurOns();  currency = "EUR"; }
            default -> throw new UnsupportedOperationException(
                    "Unsupported precious category: " + cat + " for " + type);
        }
        if (price == null) {
            throw new IllegalStateException(
                    "Precious metal price unavailable for: " + type + ":" + cat);
        }

        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(currency);
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        holding.setName(preciousDisplayName(type));

        // Günlük değişim — 1W history son iki noktasından (watchlist ile aynı yöntem)
        try {
            String histCurrency = currency;
            PreciousMetalHistoryResponse hist1w = preciousMetalService.getHistory(type, "1W", histCurrency);
            PortfolioHistoryPoints.PreciousWindow pp = PortfolioHistoryPoints.preciousWindow(hist1w);
            if (pp.latest() != null && pp.prev() != null) {
                BigDecimal last = PortfolioHistoryPoints.preciousPointValue(pp.latest(), cat);
                BigDecimal prev = PortfolioHistoryPoints.preciousPointValue(pp.prev(), cat);
                if (last != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal ch = last.subtract(prev);
                    holding.setChange(ch);
                    holding.setChangePercent(ch.divide(prev, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }
        } catch (Exception e) {
            log.debug("Precious 1W change unavailable for {}:{}: {}", type, cat, e.getMessage());
        }

        // 52 hafta + MA20/MA50 — 1Y history (cat birimi ile)
        try {
            PreciousMetalHistoryResponse hist1y = preciousMetalService.getHistory(type, "1Y", currency);
            if (hist1y != null && hist1y.getPoints() != null) {
                List<BigDecimal> closes1y = hist1y.getPoints().stream()
                        .map(p -> PortfolioHistoryPoints.preciousPointValue(p, cat))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!closes1y.isEmpty()) {
                    holding.setFiftyTwoWeekHigh(closes1y.stream().max(BigDecimal::compareTo).orElse(null));
                    holding.setFiftyTwoWeekLow(closes1y.stream().min(BigDecimal::compareTo).orElse(null));
                    applyMasFromCloses(holding, closes1y);
                }
            }
        } catch (Exception e) {
            log.debug("Precious 1Y 52w/MA unavailable for {}:{}: {}", type, cat, e.getMessage());
        }
        // BIST precious için güvenilir işlem hacmi yok — "Hacim" doldurulmaz.
    }

    private static String preciousDisplayName(PreciousMetalType type) {
        return switch (type) {
            case PLATINUM -> "Platin";
            case PALLADIUM -> "Paladyum";
            default -> type.name();
        };
    }

    private BigDecimal fetchUsdTryRate() {
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            return latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItem::getSell)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("USD/TRY rate unavailable: {}", e.getMessage());
            return null;
        }
    }
}
