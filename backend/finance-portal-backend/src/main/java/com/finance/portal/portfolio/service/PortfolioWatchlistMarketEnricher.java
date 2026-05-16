package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.market.infrastructure.external.precious.PreciousMetalType;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import com.finance.portal.portfolio.application.port.WatchlistMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioHistoryPoints;
import com.finance.portal.portfolio.service.support.RasyonetFundLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * İzleme listesi satırları için canlı piyasa alanlarını doldurur (portfolio holding mantığından ayrı).
 */
@Service
public class PortfolioWatchlistMarketEnricher implements WatchlistMarketEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioWatchlistMarketEnricher.class);

    private final CryptoMarketService cryptoMarketService;
    private final StockQueryService stockQueryService;
    private final MarketFxService marketFxService;
    private final RasyonetFundService rasyonetFundService;
    private final SilverMarketService silverMarketService;
    private final PreciousMetalService preciousMetalService;
    private final YahooCommodityService yahooCommodityService;
    private final GoldMarketService goldMarketService;
    private final EvdsBondService evdsBondService;

    public PortfolioWatchlistMarketEnricher(CryptoMarketService cryptoMarketService,
                                            StockQueryService stockQueryService,
                                            MarketFxService marketFxService,
                                            RasyonetFundService rasyonetFundService,
                                            SilverMarketService silverMarketService,
                                            PreciousMetalService preciousMetalService,
                                            YahooCommodityService yahooCommodityService,
                                            GoldMarketService goldMarketService,
                                            EvdsBondService evdsBondService) {
        this.cryptoMarketService = cryptoMarketService;
        this.stockQueryService = stockQueryService;
        this.marketFxService = marketFxService;
        this.rasyonetFundService = rasyonetFundService;
        this.silverMarketService = silverMarketService;
        this.preciousMetalService = preciousMetalService;
        this.yahooCommodityService = yahooCommodityService;
        this.goldMarketService = goldMarketService;
        this.evdsBondService = evdsBondService;
    }

    @Override
    public void enrich(WatchlistItemResponse r) {
        try {
            AssetType type = r.getAssetType();
            String symbol = r.getSymbol();
            if (type == null || symbol == null || symbol.isBlank()) {
                return;
            }

            switch (type) {
                case CRYPTO -> enrichCrypto(r, symbol);
                case STOCK, FUTURE -> enrichStockLike(r, symbol);
                case FUND -> enrichFund(r, symbol);
                case FX -> enrichFx(r, symbol);
                case GOLD -> enrichGold(r, symbol);
                case COMMODITY -> enrichCommodity(r, symbol);
                case BOND -> enrichBond(r, symbol);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Watchlist live price not supported for assetType={} symbol={}",
                    r.getAssetType(), r.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to enrich watchlist item assetType={} symbol={}: {}",
                    r.getAssetType(), r.getSymbol(), ex.getMessage());
        }
    }

    private void enrichFx(WatchlistItemResponse r, String symbol) {
        String sym = symbol.toUpperCase();
        FxLatestResponse fx = marketFxService.getTcmbLatestRates(sym);
        FxRateItemDto rate = fx.getRates().stream()
                .filter(x -> sym.equalsIgnoreCase(x.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("FX rate not found: " + sym));

        BigDecimal buy = rate.getBuy();
        BigDecimal sell = rate.getSell();
        if (rate.getUnit() > 1) {
            BigDecimal u = BigDecimal.valueOf(rate.getUnit());
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
    }

    private void enrichCrypto(WatchlistItemResponse r, String symbol) {
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
    }

    private void enrichStockLike(WatchlistItemResponse r, String symbol) {
        StockSummary s = stockQueryService.getStockSummary(symbol.toUpperCase());
        applyStockSummary(r, s);
    }

    private void applyStockSummary(WatchlistItemResponse r, StockSummary s) {
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

    private void enrichFund(WatchlistItemResponse r, String symbol) {
        String code = symbol.trim().toUpperCase();

        RasyonetFundDto listed = RasyonetFundLookup.findByCode(rasyonetFundService, code);
        List<String> detailSources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            detailSources.add(listed.getSourceCode().trim().toUpperCase());
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!detailSources.contains(sc)) {
                detailSources.add(sc);
            }
        }

        for (String sc : detailSources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyRasyonetFundDetail(r, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyRasyonetFundListRow(r, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    private void applyRasyonetFundListRow(WatchlistItemResponse r, RasyonetFundDto f) {
        r.setLastPrice(f.getPrice());
        r.setCurrency("TRY");
        fillFundChangeFromDailyPercent(r, f.getPrice(), f.getReturnOneDay());
        mapFundMetadataFromListDto(r, f);
        r.setAsOf(LocalDateTime.now());
    }

    private void applyRasyonetFundDetail(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        BigDecimal nav = d.getPrice();
        r.setLastPrice(nav);
        String cur = d.getCurrencyCode();
        r.setCurrency(cur != null && !cur.isBlank() ? cur : "TRY");
        mapFundMetadataFromDetailDto(r, d);

        List<RasyonetFundDetailDto.PricePoint> ph = d.getPriceHistory();
        if (!applyFundOhlcFromRasyonetHistory(r, ph, nav)) {
            fillFundChangeFromDailyPercent(r, nav, d.getReturnOneDay());
        }
        if (r.getOpen() == null && r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
        r.setAsOf(LocalDateTime.now());
    }

    private void mapFundMetadataFromListDto(WatchlistItemResponse r, RasyonetFundDto f) {
        if (f == null) {
            return;
        }
        if (f.getName() != null && !f.getName().isBlank()) {
            r.setFundName(f.getName());
        }
        if (f.getFundType() != null && !f.getFundType().isBlank()) {
            r.setFundType(f.getFundType());
        }
        r.setFundReturnOneMonth(f.getReturnOneMonth());
        r.setFundReturnThreeMonths(f.getReturnThreeMonths());
        r.setFundReturnYtd(f.getReturnYearToDate());
        r.setFundReturnOneYear(f.getReturnOneYear());
        r.setFundRiskLevel(f.getRiskLevel());
    }

    private void mapFundMetadataFromDetailDto(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        if (d == null) {
            return;
        }
        if (d.getName() != null && !d.getName().isBlank()) {
            r.setFundName(d.getName());
        }
        if (d.getFundType() != null && !d.getFundType().isBlank()) {
            r.setFundType(d.getFundType());
        }
        r.setFundReturnOneMonth(d.getReturnOneMonth());
        r.setFundReturnThreeMonths(d.getReturnThreeMonths());
        r.setFundReturnYtd(d.getReturnYearToDate());
        r.setFundReturnOneYear(d.getReturnOneYear());
        r.setFundRiskLevel(d.getRiskLevel());
    }

    private boolean applyFundOhlcFromRasyonetHistory(WatchlistItemResponse r,
            List<RasyonetFundDetailDto.PricePoint> ph, BigDecimal nav) {
        if (ph == null || ph.isEmpty() || nav == null) {
            return false;
        }
        int n = ph.size();
        int windowStart = Math.max(0, n - 5);
        BigDecimal maxP = null;
        BigDecimal minP = null;
        for (int i = windowStart; i < n; i++) {
            BigDecimal p = ph.get(i).getPrice();
            if (p == null) {
                continue;
            }
            maxP = maxP == null ? p : maxP.max(p);
            minP = minP == null ? p : minP.min(p);
        }
        maxP = maxP == null ? nav : maxP.max(nav);
        minP = minP == null ? nav : minP.min(nav);
        r.setHigh(maxP.setScale(4, RoundingMode.HALF_UP));
        r.setLow(minP.setScale(4, RoundingMode.HALF_UP));

        if (n >= 2) {
            BigDecimal prevClose = ph.get(n - 2).getPrice();
            if (prevClose != null) {
                r.setOpen(prevClose.setScale(4, RoundingMode.HALF_UP));
                BigDecimal ch = nav.subtract(prevClose);
                r.setChange(ch.setScale(4, RoundingMode.HALF_UP));
                if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    r.setChangePercent(ch.divide(prevClose, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP));
                }
                return true;
            }
        }
        return false;
    }

    private void fillFundChangeFromDailyPercent(WatchlistItemResponse r, BigDecimal price, BigDecimal returnOneDayPct) {
        if (price == null || returnOneDayPct == null) {
            return;
        }
        r.setChangePercent(returnOneDayPct.setScale(2, RoundingMode.HALF_UP));
        BigDecimal denom = BigDecimal.ONE.add(
                returnOneDayPct.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP));
        if (denom.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal impliedPrev = price.divide(denom, 8, RoundingMode.HALF_UP);
        r.setChange(price.subtract(impliedPrev).setScale(4, RoundingMode.HALF_UP));
        if (r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
    }

    private void enrichCommodity(WatchlistItemResponse r, String symbol) {
        if (symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal = parts[0].toUpperCase();
            String cat = parts.length > 1 ? parts[1].toUpperCase() : "";

            if ("SILVER".equals(metal)) {
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
                r.setVolume(volume);
                r.setAsOf(asOf);
                return;
            }

            if ("PLATINUM".equals(metal) || "PALLADIUM".equals(metal)) {
                PreciousMetalType type = "PLATINUM".equals(metal) ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM;
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
                    case "GRAM_TRY" -> {
                        priceVal = spot.getTryGram();
                        currencyVal = "TRY";
                    }
                    case "KG_TRY" -> {
                        priceVal = spot.getTryKg();
                        currencyVal = "TRY";
                    }
                    case "USD_ONS" -> {
                        priceVal = spot.getUsdOns();
                        currencyVal = "USD";
                    }
                    case "EUR_ONS" -> {
                        priceVal = spot.getEurOns();
                        currencyVal = "EUR";
                    }
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
                }

                r.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
                return;
            }

            throw new UnsupportedOperationException("Unsupported precious metal symbol: " + symbol);
        }

        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        r.setLastPrice(spot.getDisplayPrice());
        r.setCurrency(spot.getDisplayCurrency());
        r.setOpen(spot.getPreviousClose());
        r.setHigh(spot.getDayHigh());
        r.setLow(spot.getDayLow());
        r.setChange(spot.getChange());
        r.setChangePercent(spot.getChangePercent());
        r.setVolume(spot.getVolume());
        r.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
    }

    private void applyPreciousHighLowFromHistoryWindow(WatchlistItemResponse r,
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

    private void enrichGold(WatchlistItemResponse r, String symbol) {
        GoldSpotResponse spot = goldMarketService.getSpotGold();
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(spot.getLastUpdated());
        if (asOf == null) {
            asOf = PortfolioDateTimeParse.parseLenient(spot.getUpdatedAt());
        }
        r.setAsOf(asOf);

        String upper = symbol.toUpperCase();
        switch (upper) {
            case "GOLD" -> {
                BigDecimal onsTry = spot.getOnsTry();
                if (onsTry == null && spot.getOnsUsd() != null && spot.getUsdTry() != null) {
                    onsTry = spot.getOnsUsd().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP);
                }
                r.setLastPrice(onsTry);
                r.setCurrency("TRY");
                if (spot.getOnsHigh() != null && spot.getUsdTry() != null) {
                    r.setHigh(spot.getOnsHigh().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setHigh(spot.getOnsHigh());
                }
                if (spot.getOnsLow() != null && spot.getUsdTry() != null) {
                    r.setLow(spot.getOnsLow().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setLow(spot.getOnsLow());
                }
                if (spot.getOnsChange() != null && spot.getUsdTry() != null) {
                    r.setChange(spot.getOnsChange().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setChange(spot.getOnsChange());
                }
                r.setChangePercent(spot.getOnsChangePercent());
            }
            case "GRAM" -> {
                r.setLastPrice(spot.getGramGoldTry());
                r.setCurrency("TRY");
                r.setHigh(spot.getGramHighTry());
                r.setLow(spot.getGramLowTry());
            }
            case "CEYREK" -> {
                r.setLastPrice(spot.getQuarterGoldTry());
                r.setCurrency("TRY");
            }
            case "YARIM" -> {
                r.setLastPrice(spot.getHalfGoldTry());
                r.setCurrency("TRY");
            }
            case "TAM", "ZIYNET" -> {
                r.setLastPrice(spot.getZiynetGoldTry());
                r.setCurrency("TRY");
            }
            case "CUMHUR", "ATA" -> {
                r.setLastPrice(spot.getRepublicGoldTry());
                r.setCurrency("TRY");
            }
            case "14AYAR", "AYAR14" -> {
                r.setLastPrice(spot.getFourteenKBraceletTry() != null ? spot.getFourteenKBraceletTry() : spot.getAyar14Tl());
                r.setCurrency("TRY");
            }
            case "22AYAR", "AYAR22" -> {
                r.setLastPrice(spot.getTwentyTwoKBraceletTry() != null ? spot.getTwentyTwoKBraceletTry() : spot.getAyar22Tl());
                r.setCurrency("TRY");
            }
            default -> throw new UnsupportedOperationException("Unsupported gold symbol: " + symbol);
        }

        try {
            if ("GOLD".equals(upper)) {
                GoldHistoryResponse hist = goldMarketService.getGoldHistory("1W", "USD");
                GoldHistoryPoint lp = latestFromGoldHistory(hist);
                if (lp != null) {
                    r.setOpen(lp.getOpen());
                    r.setHigh(lp.getHigh() != null ? lp.getHigh() : r.getHigh());
                    r.setLow(lp.getLow() != null ? lp.getLow() : r.getLow());
                    r.setVolume(lp.getVolume());
                }
            } else {
                GoldHistoryResponse hist = goldMarketService.getGoldHistory("1W", "TRY");
                GoldHistoryPoint lp = latestFromGoldHistory(hist);
                if (lp != null) {
                    if ("GRAM".equals(upper)) {
                        r.setOpen(lp.getOpen());
                        r.setVolume(lp.getVolume());
                        if (lp.getClose() != null) {
                            r.setLastPrice(lp.getClose());
                        }
                        r.setHigh(lp.getHigh());
                        r.setLow(lp.getLow());
                        if (lp.getClose() != null && lp.getOpen() != null) {
                            BigDecimal ch = lp.getClose().subtract(lp.getOpen());
                            r.setChange(ch);
                            if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                                r.setChangePercent(ch.divide(lp.getOpen(), 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP));
                            }
                        }
                    } else {
                        BigDecimal gramRef = spot.getOfficialPureGoldGramTry() != null
                                ? spot.getOfficialPureGoldGramTry()
                                : spot.getGramGoldTry();
                        if (gramRef != null && gramRef.compareTo(BigDecimal.ZERO) != 0
                                && lp.getClose() != null && lp.getOpen() != null
                                && r.getLastPrice() != null) {
                            BigDecimal gramCh = lp.getClose().subtract(lp.getOpen());
                            BigDecimal ratio = r.getLastPrice().divide(gramRef, 8, RoundingMode.HALF_UP);
                            BigDecimal coinChange = gramCh.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                            r.setChange(coinChange);
                            if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                                BigDecimal gramPct = gramCh.divide(lp.getOpen(), 8, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);
                                r.setChangePercent(gramPct);
                            }
                            r.setOpen(r.getLastPrice().subtract(coinChange));
                            if (lp.getHigh() != null) {
                                r.setHigh(lp.getHigh().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                            }
                            if (lp.getLow() != null) {
                                r.setLow(lp.getLow().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                            }
                            r.setVolume(lp.getVolume());
                        } else {
                            r.setVolume(lp.getVolume());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static GoldHistoryPoint latestFromGoldHistory(GoldHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return null;
        }
        List<GoldHistoryPoint> pts = resp.getPoints();
        return pts.get(pts.size() - 1);
    }

    private void enrichBond(WatchlistItemResponse r, String instrumentCode) {
        EvdsBondInstrument bond = evdsBondService.getEvdsBondDetail(instrumentCode);
        r.setLastPrice(bond.getIndicatorValue());
        r.setChange(bond.getDailyChange());
        r.setChangePercent(bond.getDailyChangePercent());
        r.setCurrency("TRY");
        r.setRemainingDays(bond.getRemainingDays());
        r.setCouponRate(bond.getCouponRate());

        LocalDate lu = bond.getLastUpdated();
        r.setAsOf(lu != null ? lu.atStartOfDay() : LocalDateTime.now());
    }
}
