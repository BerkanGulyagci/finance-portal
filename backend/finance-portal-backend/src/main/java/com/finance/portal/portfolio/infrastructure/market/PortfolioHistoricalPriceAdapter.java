package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.funds.service.TefasFundService;
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
    private final TefasFundService tefasFundService;
    private final TcmbFxHistoryPort tcmbFxHistoryPort;
    private final MarketFxService marketFxService;
    private final EvdsBondPort evdsBondPort;
    private final GoldMarketService goldMarketService;
    private final SilverMarketService silverMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final CryptoMarketService cryptoMarketService;
    private final CryptoYahooChartService cryptoYahooChartService;
    private final ViopChartService viopChartService;

    public PortfolioHistoricalPriceAdapter(StockQueryService stockQueryService,
                                           RasyonetFundService rasyonetFundService,
                                           TefasFundService tefasFundService,
                                           TcmbFxHistoryPort tcmbFxHistoryPort,
                                           MarketFxService marketFxService,
                                           EvdsBondPort evdsBondPort,
                                           GoldMarketService goldMarketService,
                                           SilverMarketService silverMarketService,
                                           YahooCommodityService yahooCommodityService,
                                           CryptoMarketService cryptoMarketService,
                                           CryptoYahooChartService cryptoYahooChartService,
                                           ViopChartService viopChartService) {
        this.stockQueryService = stockQueryService;
        this.rasyonetFundService = rasyonetFundService;
        this.tefasFundService = tefasFundService;
        this.tcmbFxHistoryPort = tcmbFxHistoryPort;
        this.marketFxService = marketFxService;
        this.evdsBondPort = evdsBondPort;
        this.goldMarketService = goldMarketService;
        this.silverMarketService = silverMarketService;
        this.yahooCommodityService = yahooCommodityService;
        this.cryptoMarketService = cryptoMarketService;
        this.cryptoYahooChartService = cryptoYahooChartService;
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
        StockChartResponse chart = stockQueryService.getStockChartWithParams(symbol, yahooStockRange(from), "1d");
        return PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(chart.getTimestamps(), chart.getClosePrices());
    }

    private NavigableMap<LocalDate, BigDecimal> fetchFund(String symbol, LocalDate from, LocalDate to) {
        // Önce TEFAS: price-at ~10 günlük tek pencere çektiği için tek istek olur (throttle yok) →
        // 5 yıla kadar güvenilir. Yatırım fonları (YAT) için çalışır; boş dönerse (BES/OKS veya
        // çok yeni fon) Rasyonet'e düşülür.
        try {
            List<FundPriceHistoryPoint> tefas = tefasFundService.getPriceHistoryRange(symbol, "YAT", from, to);
            if (tefas != null && !tefas.isEmpty()) {
                NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
                for (FundPriceHistoryPoint p : tefas) {
                    if (p.getDate() == null || p.getPrice() == null) {
                        continue;
                    }
                    try {
                        LocalDate day = LocalDate.parse(p.getDate().substring(0, Math.min(10, p.getDate().length())));
                        map.put(day, p.getPrice());
                    } catch (Exception ignored) {
                        // skip
                    }
                }
                if (!map.isEmpty()) {
                    return map;
                }
            }
        } catch (Exception e) {
            log.debug("TEFAS fund price-at failed for {}: {}", symbol, e.getMessage());
        }

        // Rasyonet fallback (~1 yıl) — BES/OKS ve TEFAS'ta bulunmayan fonlar için.
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
            GoldHistoryResponse hist = goldMarketService.getGoldHistory(metalRange(from), "USD");
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

        GoldHistoryResponse hist = goldMarketService.getGoldHistory(metalRange(from), "TRY");
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
        CommodityHistoryResponse hist = yahooCommodityService.getHistory(symbol, commodityRange(from), "1d");
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
        // findBySymbol CoinGecko coin listesini (cache) kullanır; 429/hatada null olabilir → tolere et.
        CryptoMarketItem item = null;
        try {
            item = cryptoMarketService.findBySymbol(symbol);
        } catch (Exception ignored) {
            // CoinGecko erişilemiyor — eski tarihlerde Yahoo fallback yine de çalışır
        }

        // Eski tarih (>~1 yıl): CoinGecko market_chart zaten ~1 yıl ile sınırlı VE sık 429 veriyor
        // (price-at'i 80 sn bloke edebiliyor). Doğrudan Yahoo USD × TCMB USD/TRY fallback'ine git.
        // Sembol eşlemesi için item varsa onun ticker'ını, yoksa girdi sembolünü kullan.
        if (daysSince(from) > 366) {
            String baseSym = (item != null && item.getSymbol() != null && !item.getSymbol().isBlank())
                    ? item.getSymbol() : symbol;
            NavigableMap<LocalDate, BigDecimal> usdDerived = fetchCryptoUsdFxFallback(baseSym, from, to);
            // Yahoo ticker çakışması koruması: türetilen güncel TL değeri coin'in gerçek güncel TRY
            // fiyatından çok uzaksa (ör. HYPE-USD = farklı/ölü bir token) → yanlış token, kullanma.
            if (!usdDerived.isEmpty() && item != null && item.getCurrentPrice() != null
                    && item.getCurrentPrice().signum() > 0) {
                double ratio = usdDerived.lastEntry().getValue().doubleValue()
                        / item.getCurrentPrice().doubleValue();
                if (ratio < 0.2 || ratio > 5.0) {
                    log.debug("Crypto Yahoo fallback {} reddedildi (oran {} — yanlış token olabilir)", symbol, ratio);
                    return PortfolioHistoricalPriceSeriesSupport.emptyMap();
                }
            }
            return usdDerived;
        }

        // Yakın tarih (≤1 yıl): CoinGecko TRY
        if (item == null || item.getId() == null) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        try {
            Map<String, Object> chart = cryptoMarketService.getMarketChart(
                    item.getId(), cryptoDays(from), "try", null, null);
            return PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(chart);
        } catch (Exception e) {
            log.debug("CoinGecko market chart failed for {}: {}", symbol, e.getMessage());
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
    }

    /**
     * Kripto eski tarihler için TL serisi: Yahoo Finance USD kapanışları (BTC-USD vb.) × o güne ait
     * TCMB USD/TRY satış kuru (forward-fill). CoinGecko'nun ~1 yıl ile sınırlı TRY market_chart'ı
     * eski tarihlerde boş kaldığında devreye girer. Yahoo line chart yalnız 5y (haftalık) ve max
     * (aylık) aralıklarını desteklediği için seri kaba; floorEntry ile en yakın önceki kapanış kullanılır.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchCryptoUsdFxFallback(
            String baseSymbol, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        if (baseSymbol == null || baseSymbol.isBlank()) {
            return out;
        }
        String range = daysSince(from) <= 1830 ? "5y" : "max";
        StockChartResponse usdChart;
        try {
            usdChart = cryptoYahooChartService.getLineChart(baseSymbol, range, "USD");
        } catch (Exception e) {
            log.debug("Crypto Yahoo USD fallback failed for {}: {}", baseSymbol, e.getMessage());
            return out;
        }
        if (usdChart == null) {
            return out;
        }
        NavigableMap<LocalDate, BigDecimal> usdByDay = PortfolioHistoricalPriceSeriesSupport
                .fromEpochCloses(usdChart.getTimestamps(), usdChart.getClosePrices());
        if (usdByDay.isEmpty()) {
            return out;
        }

        NavigableMap<LocalDate, BigDecimal> usdTryByDay =
                CommodityHistoricalTryConversion.loadUsdTryHistory(tcmbFxHistoryPort, from.minusDays(10), to);
        BigDecimal latestUsdTry = fetchUsdTryRate();
        // Kur o günden öncesine ait değilse en eski mevcut kuru kullan (eski coin günleri TCMB
        // geçmişinin başından da eski olabilir).
        BigDecimal earliestUsdTry = usdTryByDay.isEmpty() ? null : usdTryByDay.firstEntry().getValue();
        if (usdTryByDay.isEmpty() && latestUsdTry == null) {
            return out;
        }

        for (Map.Entry<LocalDate, BigDecimal> e : usdByDay.entrySet()) {
            LocalDate day = e.getKey();
            if (day.isAfter(to)) {
                continue;
            }
            BigDecimal fallbackRate = earliestUsdTry != null ? earliestUsdTry : latestUsdTry;
            BigDecimal tryClose = CommodityHistoricalTryConversion.convertUsdCloseToTry(
                    e.getValue(), day, usdTryByDay, fallbackRate);
            if (tryClose != null) {
                out.put(day, tryClose);
            }
        }
        return out;
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
            hist = silverMarketService.getSilverHistory(metalRange(from), currency);
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

    /** VİOP grafik periyotları — en uzundan kısaya (kaynak aralıklı boş/hata dönebilir). */
    private static final List<ViopChartPeriod> VIOP_PERIODS_LONGEST_FIRST = List.of(
            ViopChartPeriod.ONE_YEAR,
            ViopChartPeriod.SIX_MONTHS,
            ViopChartPeriod.THREE_MONTHS,
            ViopChartPeriod.ONE_MONTH,
            ViopChartPeriod.ONE_WEEK);

    /**
     * VİOP tarihsel kapanış serisi. İş Yatırım kaynağı aynı kontrat için bazen veri,
     * bazen boş/hata döndürdüğü (flaky) ve yeni listelenen kontratlar 1 yıllık veriye
     * sahip olmadığı için: hedef periyottan başlayıp daha kısa periyotlara düşerek
     * <b>veri dönen ilk (en uzun) periyodu</b> kullanır. Böylece geçici hatalarda veya
     * kısa geçmişli kontratlarda varlık grafikten tamamen düşmez.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchFuture(String symbol, LocalDate from, LocalDate to) {
        // İş Yatırım kaynağı [şimdi - periyot, şimdi] penceresi döndürür; bu yüzden periyot,
        // istenen `from`'dan bugüne kadar olan aralığa göre seçilir (yoksa `from..to` dar bir
        // aralıkta hep ONE_WEEK çıkıp eski tarihler pencereye hiç girmez). VİOP kaynak üst sınırı
        // ~1 yıl olduğundan ~1 yıldan eski tarihler kaynakta zaten yok.
        ViopChartPeriod target = resolveViopPeriod(from, LocalDate.now());
        boolean reachedTarget = false;
        for (ViopChartPeriod period : VIOP_PERIODS_LONGEST_FIRST) {
            if (!reachedTarget) {
                if (period != target) {
                    continue;
                }
                reachedTarget = true;
            }
            try {
                List<ViopChartPoint> points = viopChartService.getChart(symbol, period);
                NavigableMap<LocalDate, BigDecimal> filtered = filterByDateRange(
                        PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(points), from, to);
                if (!filtered.isEmpty()) {
                    return filtered;
                }
                log.debug("VIOP chart {} period {} boş, daha kısa periyot deneniyor", symbol, period);
            } catch (UnsupportedViopContractException e) {
                // Kontrat gerçekten desteklenmiyor — kısa periyot da işe yaramaz.
                log.debug("VIOP chart unsupported for {}: {}", symbol, e.getMessage());
                return PortfolioHistoricalPriceSeriesSupport.emptyMap();
            } catch (Exception e) {
                log.debug("VIOP chart {} period {} hata, daha kısa periyot deneniyor: {}",
                        symbol, period, e.getMessage());
            }
        }
        return PortfolioHistoricalPriceSeriesSupport.emptyMap();
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

    // ── Tarihsel aralık seçimi ────────────────────────────────────────────────
    // İstenen `from` tarihini kapsayacak kadar geriye giden kaynak aralığı seçilir.
    // Performans grafiği `from`'u 1 yılla sınırladığı için orada davranış değişmez;
    // işlem-fiyatı (price-at) sorgusu eski tarihlerde daha uzun aralık çeker.

    private static long daysSince(LocalDate from) {
        return ChronoUnit.DAYS.between(from, LocalDate.now());
    }

    /** Yahoo hisse aralığı: 1y / 2y / 5y / 10y / max. */
    private static String yahooStockRange(LocalDate from) {
        long d = daysSince(from);
        if (d <= 370) return "1y";
        if (d <= 730) return "2y";
        if (d <= 1830) return "5y";
        if (d <= 3660) return "10y";
        return "max";
    }

    /** Yahoo emtia (frontend range): 1Y / 5Y (en fazla 5 yıl). */
    private static String commodityRange(LocalDate from) {
        return daysSince(from) <= 370 ? "1Y" : "5Y";
    }

    /** Altın/Gümüş aralığı: 1Y / 5Y / ALL. */
    private static String metalRange(LocalDate from) {
        long d = daysSince(from);
        if (d <= 370) return "1Y";
        if (d <= 1830) return "5Y";
        return "ALL";
    }

    /** Kripto gün sayısı (CoinGecko): 365 / N / max. */
    private static String cryptoDays(LocalDate from) {
        long d = daysSince(from) + 5;
        if (d <= 365) return "365";
        if (d >= 1800) return "max";
        return String.valueOf(d);
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
