package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.enrich.BondHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CryptoHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FutureHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FxHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.GoldHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.StockHoldingEnricher;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioHistoryPoints;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import com.finance.portal.portfolio.service.support.RasyonetFundLookup;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Portföy pozisyonları için canlı piyasa alanlarını doldurur. */
@Component
public class PortfolioHoldingMarketEnricher implements HoldingMarketEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHoldingMarketEnricher.class);
    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final int FUND_MONEY_SCALE = 8;

    private final AssetPriceQueryService assetPriceQueryService;
    private final YahooCommodityService yahooCommodityService;
    private final SilverMarketService silverMarketService;
    private final MarketFxService marketFxService;
    private final RasyonetFundService rasyonetFundService;
    private final CentralIntegrationLogService integrationLogService;
    private final CryptoHoldingEnricher cryptoHoldingEnricher;
    private final BondHoldingEnricher bondHoldingEnricher;
    private final StockHoldingEnricher stockHoldingEnricher;
    private final FutureHoldingEnricher futureHoldingEnricher;
    private final FxHoldingEnricher fxHoldingEnricher;
    private final GoldHoldingEnricher goldHoldingEnricher;

    public PortfolioHoldingMarketEnricher(AssetPriceQueryService assetPriceQueryService,
                                          YahooCommodityService yahooCommodityService,
                                          SilverMarketService silverMarketService,
                                          MarketFxService marketFxService,
                                          RasyonetFundService rasyonetFundService,
                                          CentralIntegrationLogService integrationLogService,
                                          CryptoHoldingEnricher cryptoHoldingEnricher,
                                          BondHoldingEnricher bondHoldingEnricher,
                                          StockHoldingEnricher stockHoldingEnricher,
                                          FutureHoldingEnricher futureHoldingEnricher,
                                          FxHoldingEnricher fxHoldingEnricher,
                                          GoldHoldingEnricher goldHoldingEnricher) {
        this.assetPriceQueryService = assetPriceQueryService;
        this.yahooCommodityService = yahooCommodityService;
        this.silverMarketService = silverMarketService;
        this.marketFxService = marketFxService;
        this.rasyonetFundService = rasyonetFundService;
        this.integrationLogService = integrationLogService;
        this.cryptoHoldingEnricher = cryptoHoldingEnricher;
        this.bondHoldingEnricher = bondHoldingEnricher;
        this.stockHoldingEnricher = stockHoldingEnricher;
        this.futureHoldingEnricher = futureHoldingEnricher;
        this.fxHoldingEnricher = fxHoldingEnricher;
        this.goldHoldingEnricher = goldHoldingEnricher;
    }

    @Override
    @WithSpan("PortfolioEnrich.holding")
    public void enrich(PortfolioHoldingResponse holding) {
        try {
            AssetType type = holding.getAssetType();
            Span.current().setAttribute("holding.asset_type", type != null ? type.name() : "null");
            Span.current().setAttribute("holding.symbol", String.valueOf(holding.getSymbol()));
            if (type == AssetType.STOCK) {
                stockHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FUTURE) {
                futureHoldingEnricher.enrich(holding);
            } else if (type == AssetType.CRYPTO) {
                cryptoHoldingEnricher.enrich(holding);
            } else if (type == AssetType.GOLD) {
                goldHoldingEnricher.enrich(holding);
            } else if (type == AssetType.COMMODITY) {
                enrichCommodityHolding(holding);
            } else if (type == AssetType.FUND) {
                enrichFundHolding(holding);
            } else if (type == AssetType.BOND) {
                bondHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FX) {
                fxHoldingEnricher.enrich(holding);
            } else {
                // Diğer / fallback
                enrichFromPriceSnapshot(holding);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Live price not supported for assetType={} symbol={}",
                    holding.getAssetType(), holding.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to fetch live price for assetType={} symbol={}: {}",
                    holding.getAssetType(), holding.getSymbol(), ex.getMessage());
            publishDegradedMarketFetch(holding.getAssetType(), holding.getSymbol());
        }
    }

    private void publishDegradedMarketFetch(AssetType assetType, String symbol) {
        String provider = resolveProviderForAssetType(assetType);
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_MARKET_DATA_FETCH_FAILED,
                "WARN",
                "Portfolio holding live price enrichment failed",
                provider,
                "portfolio_enrich",
                null,
                null,
                null,
                true,
                Map.of(
                        "assetType", assetType != null ? assetType.name() : "UNKNOWN",
                        "symbol", symbol != null ? symbol : "",
                        "degraded", true,
                        "trigger", IntegrationLogSupport.TRIGGER_API_REQUEST
                ),
                PortfolioHoldingMarketEnricher.class.getName()
        );
    }

    private static String resolveProviderForAssetType(AssetType assetType) {
        if (assetType == null) {
            return IntegrationLogSupport.PROVIDER_EXTERNAL;
        }
        return switch (assetType) {
            case STOCK, COMMODITY -> IntegrationLogSupport.PROVIDER_YAHOO;
            case CRYPTO -> IntegrationLogSupport.PROVIDER_COINGECKO;
            case FUND -> IntegrationLogSupport.PROVIDER_RASYONET;
            case FUTURE -> IntegrationLogSupport.PROVIDER_AKBANK_VIOP;
            default -> IntegrationLogSupport.PROVIDER_EXTERNAL;
        };
    }

    private void applyMasFromCloses(PortfolioHoldingResponse holding, List<BigDecimal> closes) {
        if (closes == null || closes.isEmpty()) {
            return;
        }
        BigDecimal ma20 = PortfolioMovingAverage.simpleMa(closes, 20);
        BigDecimal ma50 = PortfolioMovingAverage.simpleMa(closes, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
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

    private void applyYahooCommodityMas(PortfolioHoldingResponse holding, String symbol) {
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

    /** FX, BOND gibi basit fiyat snapshot yeterli olan tipler için. */
    private void enrichFromPriceSnapshot(PortfolioHoldingResponse holding) {
        AssetPriceSnapshot snapshot = assetPriceQueryService.getCurrentPrice(
                holding.getAssetType(), holding.getSymbol());

        BigDecimal currentPrice = snapshot.getPrice();
        BigDecimal marketValue  = currentPrice.multiply(holding.getTotalQuantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal profitLoss   = marketValue.subtract(holding.getTotalCost())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(currentPrice);
        holding.setMarketValue(marketValue);
        holding.setProfitLoss(profitLoss);
        holding.setCurrency(snapshot.getCurrency());
        holding.setAsOf(snapshot.getAsOf());
    }


    /**
     * COMMODITY: SILVER:GRAM_TRY gibi BIST semboller için SilverMarketService,
     * diğerleri için YahooCommodityService (NG=F, CL=F vb.).
     * CommoditySpotDto zaten change, changePercent, dayHigh/Low, weekHigh52/Low52, volume içeriyor.
     */
    private void enrichCommodityHolding(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol();

        if (symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal = parts[0].toUpperCase();
            String cat   = parts.length > 1 ? parts[1].toUpperCase() : "";
            if ("SILVER".equals(metal)) {
                enrichSilverHolding(holding, cat);
                return;
            }
        }

        // Yahoo Finance destekli emtia (NG=F, CL=F, GC=F vb.)
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        BigDecimal price = spot.getDisplayPrice() != null ? spot.getDisplayPrice() : spot.getRawPrice();
        if (price == null) throw new IllegalStateException("Commodity price unavailable: " + symbol);

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        String displayName = spot.getDisplayNameTr() != null ? spot.getDisplayNameTr() : spot.getDisplayNameEn();
        if (displayName != null) holding.setName(displayName);
        holding.setChange(spot.getChange());
        holding.setChangePercent(spot.getChangePercent());
        // Emtia için güvenilir işlem hacmi gelmiyor → "Hacim" doldurulmaz.
        holding.setDayHigh(spot.getDayHigh());
        holding.setDayLow(spot.getDayLow());
        holding.setFiftyTwoWeekHigh(spot.getWeekHigh52());
        holding.setFiftyTwoWeekLow(spot.getWeekLow52());
        applyYahooCommodityMas(holding, symbol);
    }

    /** SILVER:GRAM_TRY / SILVER:KG_TRY / SILVER:USD_ONS gibi BIST gümüş sembolleri. */
    private void enrichSilverHolding(PortfolioHoldingResponse holding, String cat) {
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

        if (price == null) throw new IllegalStateException("Silver price unavailable for cat: " + cat);

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

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

        // 52-week range — 1Y history min/max
        try {
            String histCurrency = "USD".equals(currency) ? "USD" : "TRY";
            SilverHistoryResponse hist1y = silverMarketService.getSilverHistory("1Y", histCurrency);
            if (hist1y != null && hist1y.getPoints() != null) {
                List<BigDecimal> closes1y = hist1y.getPoints().stream()
                        .map(SilverHistoryPoint::getClose)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList());
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
     * FUND: Rasyonet kart / liste — NAV, getiriler, günlük değişim alanları, ~1 yıl NAV geçmişi (52w / MA).
     */
    private void enrichFundHolding(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol().trim().toUpperCase();

        RasyonetFundDto listed = RasyonetFundLookup.findByCode(rasyonetFundService, code);
        List<String> sources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            sources.add(listed.getSourceCode().trim().toUpperCase());
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!sources.contains(sc)) sources.add(sc);
        }

        for (String sc : sources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyFundRichDetailToHolding(holding, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyFundRichDetailFromListDto(holding, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    /**
     * FUND: Rasyonet zengin kart — NAV, getiriler, günlük % → {@code changePercent},
     * pay başı günlük TL farkı → {@code change} (HoldingsTable günlük TL = qty × change),
     * son ~1 yıl NAV geçmişinden 52 hafta aralığı + MA20/MA50 (trend yedek).
     */
    private void applyFundRichDetailToHolding(PortfolioHoldingResponse holding, RasyonetFundDetailDto d) {
        BigDecimal price = d.getPrice();
        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        String cur = d.getCurrencyCode();
        String safeCurrency = isValidIsoCurrency(cur) ? cur : "TRY";
        holding.setCurrency(safeCurrency);
        holding.setAsOf(LocalDateTime.now());
        if (d.getName() != null && !d.getName().isBlank()) {
            holding.setName(d.getName());
        }
        holding.setReturnOneDay(d.getReturnOneDay());
        holding.setReturnOneMonth(d.getReturnOneMonth());
        holding.setReturnThreeMonths(d.getReturnThreeMonths());

        mapFundDailyChangeToHolding(holding, price, d.getReturnOneDay());
        applyFundNavHistoryStats(holding, d.getPriceHistory(), price);
    }

    /** Liste endpoint'inden gelen fiyat; fiyat geçmişi yok → günlük %/TL yine map edilir, 52w/MA boş kalabilir. */
    private void applyFundRichDetailFromListDto(PortfolioHoldingResponse holding, RasyonetFundDto listed) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setPrice(listed.getPrice());
        d.setCurrencyCode("TRY");
        d.setName(listed.getName());
        d.setReturnOneDay(listed.getReturnOneDay());
        d.setReturnOneMonth(listed.getReturnOneMonth());
        d.setReturnThreeMonths(listed.getReturnThreeMonths());
        d.setPriceHistory(null);
        applyFundRichDetailToHolding(holding, d);
    }

    /** returnOneDay (%) → changePercent; pay başı günlük TL → change. */
    private void mapFundDailyChangeToHolding(PortfolioHoldingResponse holding,
                                             BigDecimal price,
                                             BigDecimal returnOneDayPct) {
        if (returnOneDayPct == null) {
            holding.setChange(null);
            holding.setChangePercent(null);
            return;
        }
        holding.setChangePercent(returnOneDayPct);
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = price.multiply(returnOneDayPct)
                    .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            holding.setChange(delta);
        } else {
            holding.setChange(null);
        }
    }

    /**
     * Rasyonet {@code LastYearReturnPrice} serisinden min/max NAV ve kapanış serisi MA20/MA50.
     */
    private void applyFundNavHistoryStats(PortfolioHoldingResponse holding,
                                          List<RasyonetFundDetailDto.PricePoint> rawPh,
                                          BigDecimal currentNav) {
        if (rawPh == null || rawPh.isEmpty()) {
            return;
        }
        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>(rawPh);
        ph.sort(Comparator.comparing(RasyonetFundDetailDto.PricePoint::getDate,
                Comparator.nullsLast(String::compareTo)));

        List<BigDecimal> closes = ph.stream()
                .map(RasyonetFundDetailDto.PricePoint::getPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (closes.isEmpty()) {
            return;
        }

        BigDecimal hi = closes.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal lo = closes.stream().min(BigDecimal::compareTo).orElse(null);
        if (currentNav != null) {
            hi = hi == null ? currentNav : hi.max(currentNav);
            lo = lo == null ? currentNav : lo.min(currentNav);
        }
        holding.setFiftyTwoWeekHigh(hi);
        holding.setFiftyTwoWeekLow(lo);

        List<BigDecimal> forMa = new ArrayList<>(closes);
        if (currentNav != null
                && (forMa.isEmpty() || forMa.get(forMa.size() - 1).compareTo(currentNav) != 0)) {
            forMa.add(currentNav);
        }
        holding.setMa20(PortfolioMovingAverage.simpleMa(forMa, 20));
        holding.setMa50(PortfolioMovingAverage.simpleMa(forMa, 50));
    }

    /**
     * Verilen string'in bilinen ISO 4217 para birimi kodu olup olmadığını kontrol eder.
     * Rasyonet'in kaynak kodları (TMF, TPF, TAF) gibi değerleri filtreler.
     */
    private static boolean isValidIsoCurrency(String code) {
        if (code == null || code.isBlank()) return false;
        try {
            java.util.Currency.getInstance(code.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
