package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Piyasa servislerinden günlük kapanış serisi çeker (canlı zenginleştirmeden bağımsız).
 */
@Component
public class PortfolioHistoricalPriceAdapter implements PortfolioHistoricalPricePort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHistoricalPriceAdapter.class);

    private static final BigDecimal GOLD_FINENESS_22K = new BigDecimal("0.9166");
    private static final BigDecimal GOLD_FINENESS_14K = new BigDecimal("0.5850");
    private static final BigDecimal GOLD_GROSS_QUARTER = new BigDecimal("1.754");
    private static final BigDecimal GOLD_GROSS_HALF = new BigDecimal("3.508");
    private static final BigDecimal GOLD_GROSS_ZIYNET = new BigDecimal("7.016");
    private static final BigDecimal GOLD_GROSS_REPUBLIC = new BigDecimal("7.216");

    private final StockQueryService stockQueryService;
    private final RasyonetFundService rasyonetFundService;
    private final TcmbFxHistoryPort tcmbFxHistoryPort;
    private final MarketFxService marketFxService;
    private final EvdsBondPort evdsBondPort;
    private final GoldMarketService goldMarketService;
    private final SilverMarketService silverMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final CryptoMarketService cryptoMarketService;
    private final ViopChartService viopChartService;

    public PortfolioHistoricalPriceAdapter(StockQueryService stockQueryService,
                                           RasyonetFundService rasyonetFundService,
                                           TcmbFxHistoryPort tcmbFxHistoryPort,
                                           MarketFxService marketFxService,
                                           EvdsBondPort evdsBondPort,
                                           GoldMarketService goldMarketService,
                                           SilverMarketService silverMarketService,
                                           YahooCommodityService yahooCommodityService,
                                           CryptoMarketService cryptoMarketService,
                                           ViopChartService viopChartService) {
        this.stockQueryService = stockQueryService;
        this.rasyonetFundService = rasyonetFundService;
        this.tcmbFxHistoryPort = tcmbFxHistoryPort;
        this.marketFxService = marketFxService;
        this.evdsBondPort = evdsBondPort;
        this.goldMarketService = goldMarketService;
        this.silverMarketService = silverMarketService;
        this.yahooCommodityService = yahooCommodityService;
        this.cryptoMarketService = cryptoMarketService;
        this.viopChartService = viopChartService;
    }

    @Override
    public Optional<NavigableMap<LocalDate, BigDecimal>> fetchDailyClosePrices(
            AssetType assetType,
            String symbol,
            LocalDate from,
            LocalDate to) {
        try {
            NavigableMap<LocalDate, BigDecimal> series = switch (assetType) {
                case STOCK -> fetchStock(symbol, from, to);
                case FUND -> fetchFund(symbol, from, to);
                case FX -> fetchFx(symbol, from, to);
                case BOND -> fetchBond(symbol, from, to);
                case GOLD -> fetchGold(symbol, from, to);
                case COMMODITY -> fetchCommodity(symbol, from, to);
                case CRYPTO -> fetchCrypto(symbol, from, to);
                case FUTURE -> fetchFuture(symbol, from, to);
            };
            if (series == null || series.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(series);
        } catch (Exception e) {
            log.debug("Historical prices unavailable for {} {}: {}", assetType, symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private NavigableMap<LocalDate, BigDecimal> fetchStock(String symbol, LocalDate from, LocalDate to) {
        StockChartResponse chart = stockQueryService.getStockChartWithParams(symbol, "1y", "1d");
        return PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(chart.getTimestamps(), chart.getClosePrices());
    }

    private NavigableMap<LocalDate, BigDecimal> fetchFund(String symbol, LocalDate from, LocalDate to) {
        RasyonetFundDetailDto detail = rasyonetFundService.getFundDetailRich(symbol);
        if (detail == null || detail.getPriceHistory() == null) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (RasyonetFundDetailDto.PricePoint p : detail.getPriceHistory()) {
            if (p.getDate() == null || p.getPrice() == null) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(p.getDate().substring(0, Math.min(10, p.getDate().length())));
                PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, p.getPrice(), from, to);
            } catch (Exception ignored) {
                // skip
            }
        }
        return map;
    }

    private NavigableMap<LocalDate, BigDecimal> fetchFx(String symbol, LocalDate from, LocalDate to) {
        String sym = symbol != null ? symbol.toUpperCase(Locale.ROOT) : "";
        int unit = 1;
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates(sym);
            if (latest != null && latest.getRates() != null) {
                FxRateItem rate = latest.getRates().stream()
                        .filter(r -> sym.equalsIgnoreCase(r.getSymbol()))
                        .findFirst()
                        .orElse(null);
                if (rate != null && rate.getUnit() > 1) {
                    unit = rate.getUnit();
                }
            }
        } catch (Exception e) {
            log.debug("FX unit lookup skipped for {}: {}", sym, e.getMessage());
        }
        BigDecimal unitBd = BigDecimal.valueOf(unit);

        List<FxHistoryPoint> points = tcmbFxHistoryPort.fetchHistory(sym, from, to);
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (FxHistoryPoint p : points) {
            if (p.getDate() == null || p.getClose() == null) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(p.getDate().substring(0, 10));
                BigDecimal close = unit > 1
                        ? p.getClose().divide(unitBd, 6, RoundingMode.HALF_UP)
                        : p.getClose();
                map.put(day, close);
            } catch (Exception ignored) {
                // skip
            }
        }
        return map;
    }

    private NavigableMap<LocalDate, BigDecimal> fetchBond(String symbol, LocalDate from, LocalDate to) {
        List<EvdsSeriesPoint> points = evdsBondPort.fetchIndicatorValues(symbol, from, to);
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (EvdsSeriesPoint p : points) {
            if (p.getDate() != null && p.getValue() != null) {
                map.put(p.getDate(), p.getValue());
            }
        }
        return map;
    }

    private NavigableMap<LocalDate, BigDecimal> fetchGold(String symbol, LocalDate from, LocalDate to) {
        String upper = symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();

        if ("GOLD".equals(upper)) {
            BigDecimal usdTry = fetchUsdTryRate();
            if (usdTry == null) {
                return map;
            }
            GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "USD");
            if (hist == null || hist.getPoints() == null) {
                return map;
            }
            for (GoldHistoryPoint pt : hist.getPoints()) {
                if (pt.getDate() == null || pt.getClose() == null) {
                    continue;
                }
                try {
                    LocalDate day = LocalDate.parse(pt.getDate().substring(0, 10));
                    BigDecimal tryPrice = pt.getClose().multiply(usdTry).setScale(6, RoundingMode.HALF_UP);
                    PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, tryPrice, from, to);
                } catch (Exception ignored) {
                    // skip
                }
            }
            return map;
        }

        GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "TRY");
        if (hist == null || hist.getPoints() == null) {
            return map;
        }
        BigDecimal factor = goldTheoryFactor(upper);
        for (GoldHistoryPoint pt : hist.getPoints()) {
            if (pt.getDate() == null || pt.getClose() == null) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(pt.getDate().substring(0, 10));
                BigDecimal price = pt.getClose();
                if (factor != null) {
                    price = price.multiply(factor).setScale(6, RoundingMode.HALF_UP);
                }
                PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, price, from, to);
            } catch (Exception ignored) {
                // skip
            }
        }
        return map;
    }

    /**
     * Yahoo emtia (NG=F, CL=F, …): tarihsel USD kapanış → TRY (TCMB günlük satış, forward-fill).
     * Kur yoksa MVP: güncel USD/TRY satış kuru (spot {@code applyTryDisplayPrices} ile uyumlu).
     * {@code SILVER:…} sembolleri {@link #fetchSilver} ile ayrı işlenir.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchCommodity(String symbol, LocalDate from, LocalDate to) {
        if (isSilverCommoditySymbol(symbol)) {
            return fetchSilver(symbol, from, to);
        }
        CommodityHistoryResponse hist = yahooCommodityService.getHistory(symbol, "1Y", "1d");
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (hist == null || hist.getPoints() == null) {
            return map;
        }

        NavigableMap<LocalDate, BigDecimal> usdTryByDay =
                CommodityHistoricalTryConversion.loadUsdTryHistory(tcmbFxHistoryPort, from, to);
        BigDecimal latestUsdTry = fetchUsdTryRate();
        if (usdTryByDay.isEmpty() && latestUsdTry == null) {
            log.warn("Yahoo commodity {}: USD/TRY rate unavailable; historical series skipped", symbol);
            return map;
        }
        if (usdTryByDay.isEmpty()) {
            log.debug(
                    "Yahoo commodity {}: MVP — historical USD prices converted with latest TCMB USD/TRY sell rate",
                    symbol);
        }

        for (CommodityHistoryPointDto pt : hist.getPoints()) {
            if (pt.getDate() == null) {
                continue;
            }
            BigDecimal usdClose = pt.getDisplayClose() != null ? pt.getDisplayClose() : pt.getRawClose();
            if (usdClose == null || usdClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(pt.getDate());
                BigDecimal tryClose = CommodityHistoricalTryConversion.convertUsdCloseToTry(
                        usdClose, day, usdTryByDay, latestUsdTry);
                if (tryClose == null) {
                    continue;
                }
                PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, tryClose, from, to);
            } catch (Exception ignored) {
                // skip
            }
        }
        return map;
    }

    private NavigableMap<LocalDate, BigDecimal> fetchCrypto(String symbol, LocalDate from, LocalDate to) {
        CryptoMarketItem item = cryptoMarketService.findBySymbol(symbol);
        if (item == null || item.getId() == null) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        Map<String, Object> chart = cryptoMarketService.getMarketChart(item.getId(), "365", "try", null, null);
        return PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(chart);
    }

    /**
     * BIST gümüş: portföyde {@code SILVER:GRAM_TRY} vb. COMMODITY sembolleri.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchSilver(String symbol, LocalDate from, LocalDate to) {
        String upper = symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        String cat = "GRAM_TRY";
        if (upper.contains(":")) {
            String[] parts = upper.split(":", 2);
            if (parts.length > 1 && !parts[1].isBlank()) {
                cat = parts[1].trim();
            }
        }

        String currency = cat.contains("USD") ? "USD" : "TRY";
        SilverHistoryResponse hist;
        try {
            hist = silverMarketService.getSilverHistory("1Y", currency);
        } catch (Exception e) {
            log.debug("Silver history failed for {}: {}", symbol, e.getMessage());
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }

        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (SilverHistoryPoint pt : hist.getPoints()) {
            if (pt.getDate() == null) {
                continue;
            }
            BigDecimal price = resolveSilverClose(pt, cat);
            if (price == null) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(pt.getDate().substring(0, Math.min(10, pt.getDate().length())));
                PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, price, from, to);
            } catch (Exception ignored) {
                // skip malformed date
            }
        }
        return map;
    }

    private static BigDecimal resolveSilverClose(SilverHistoryPoint pt, String cat) {
        return switch (cat) {
            case "KG_TRY" -> {
                if (pt.getWeightedAverageTryKg() != null) {
                    yield pt.getWeightedAverageTryKg();
                }
                yield pt.getCloseTryKg();
            }
            case "USD_ONS" -> {
                if (pt.getWeightedAverageUsdOns() != null) {
                    yield pt.getWeightedAverageUsdOns();
                }
                yield pt.getClose();
            }
            default -> pt.getClose();
        };
    }

    public static String exclusionReason(AssetType assetType, String symbol) {
        if (assetType == AssetType.FUTURE) {
            return "VİOP kontratı için tarihsel fiyat bulunamadı.";
        }
        if (assetType == AssetType.COMMODITY && isSilverCommoditySymbol(symbol)) {
            return "Gümüş için tarihsel fiyat bulunamadı.";
        }
        return "Tarihsel fiyat bulunamadı.";
    }

    static boolean isSilverCommoditySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("SILVER:") || "SILVER".equals(upper)) {
            return true;
        }
        return upper.contains("GUMUS") || upper.contains("GÜMÜŞ") || upper.contains("GUMÜŞ");
    }

    private NavigableMap<LocalDate, BigDecimal> fetchFuture(String symbol, LocalDate from, LocalDate to) {
        try {
            ViopChartPeriod period = resolveViopPeriod(from, to);
            List<ViopChartPoint> points = viopChartService.getChart(symbol, period);
            NavigableMap<LocalDate, BigDecimal> map =
                    PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(points);
            return filterByDateRange(map, from, to);
        } catch (UnsupportedViopContractException e) {
            log.debug("VIOP chart unsupported for {}: {}", symbol, e.getMessage());
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
    }

    private static ViopChartPeriod resolveViopPeriod(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 7) {
            return ViopChartPeriod.ONE_WEEK;
        }
        if (days <= 35) {
            return ViopChartPeriod.ONE_MONTH;
        }
        if (days <= 95) {
            return ViopChartPeriod.THREE_MONTHS;
        }
        if (days <= 185) {
            return ViopChartPeriod.SIX_MONTHS;
        }
        return ViopChartPeriod.ONE_YEAR;
    }

    private static NavigableMap<LocalDate, BigDecimal> filterByDateRange(
            NavigableMap<LocalDate, BigDecimal> source,
            LocalDate from,
            LocalDate to) {
        if (source == null || source.isEmpty()) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        NavigableMap<LocalDate, BigDecimal> filtered = new TreeMap<>();
        source.forEach((day, price) -> {
            if (!day.isBefore(from) && !day.isAfter(to)) {
                filtered.put(day, price);
            }
        });
        return filtered;
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
            return null;
        }
    }

    private static BigDecimal goldTheoryFactor(String upper) {
        return switch (upper) {
            case "GRAM" -> null;
            case "14AYAR", "AYAR14" -> GOLD_FINENESS_14K;
            case "22AYAR", "AYAR22" -> GOLD_FINENESS_22K;
            case "CEYREK" -> GOLD_GROSS_QUARTER.multiply(GOLD_FINENESS_22K);
            case "YARIM" -> GOLD_GROSS_HALF.multiply(GOLD_FINENESS_22K);
            case "TAM", "ZIYNET" -> GOLD_GROSS_ZIYNET.multiply(GOLD_FINENESS_22K);
            case "CUMHUR", "ATA" -> GOLD_GROSS_REPUBLIC.multiply(GOLD_FINENESS_22K);
            default -> null;
        };
    }
}
