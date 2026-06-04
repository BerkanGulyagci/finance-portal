package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
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
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final EurobondService eurobondService;
    private final GoldMarketService goldMarketService;
    private final SilverMarketService silverMarketService;
    private final PreciousMetalService preciousMetalService;
    private final YahooCommodityService yahooCommodityService;
    private final CryptoMarketService cryptoMarketService;
    private final CryptoYahooChartService cryptoYahooChartService;
    private final CryptoBinanceChartService cryptoBinanceChartService;
    private final ViopChartService viopChartService;

    public PortfolioHistoricalPriceAdapter(StockQueryService stockQueryService,
                                           RasyonetFundService rasyonetFundService,
                                           TefasFundService tefasFundService,
                                           TcmbFxHistoryPort tcmbFxHistoryPort,
                                           MarketFxService marketFxService,
                                           EvdsBondPort evdsBondPort,
                                           EurobondService eurobondService,
                                           GoldMarketService goldMarketService,
                                           SilverMarketService silverMarketService,
                                           PreciousMetalService preciousMetalService,
                                           YahooCommodityService yahooCommodityService,
                                           CryptoMarketService cryptoMarketService,
                                           CryptoYahooChartService cryptoYahooChartService,
                                           CryptoBinanceChartService cryptoBinanceChartService,
                                           ViopChartService viopChartService) {
        this.stockQueryService = stockQueryService;
        this.rasyonetFundService = rasyonetFundService;
        this.tefasFundService = tefasFundService;
        this.tcmbFxHistoryPort = tcmbFxHistoryPort;
        this.marketFxService = marketFxService;
        this.evdsBondPort = evdsBondPort;
        this.eurobondService = eurobondService;
        this.goldMarketService = goldMarketService;
        this.silverMarketService = silverMarketService;
        this.preciousMetalService = preciousMetalService;
        this.yahooCommodityService = yahooCommodityService;
        this.cryptoMarketService = cryptoMarketService;
        this.cryptoYahooChartService = cryptoYahooChartService;
        this.cryptoBinanceChartService = cryptoBinanceChartService;
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
        String code = symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        // Eurobond ISIN'i → Business Insider kote serisi × tarihe uygun TCMB kuru (TL).
        if (eurobondService.currentIsins().contains(code)) {
            return fetchEurobond(code, from, to);
        }
        // EVDS "Gösterge Değeri" tüm DİBS türleri için doğrudan kullanılır — ek ölçekleme YOK.
        // TÜFE-endeksli bondlarda günlük kotasyon 100 TL nominal başına temiz fiyattır (Tier-1 ile
        // aynı konvansiyon); enflasyon endekslemesi yalnız kupon ve vade ödemesine yansır. FX/Altın
        // bondlarda EVDS değeri zaten TL bazlı. Dolayısıyla ne ek TÜFE çarpanı ne de dış FX/altın
        // çevrimi uygulanır. Reel getiri PortfolioRealReturnEnricher'da nominal seriden ayrıca türetilir.
        List<EvdsSeriesPoint> points = evdsBondPort.fetchIndicatorValues(symbol, from, to);
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (EvdsSeriesPoint p : points) {
            if (p.getDate() == null || p.getValue() == null) continue;
            map.put(p.getDate(), p.getValue());
        }
        return map;
    }

    private String guessFxBondCurrency(EvdsBondInstrument bond) {
        if (bond != null) {
            String[] hints = { bond.getType(), bond.getCbrtCode() };
            for (String hint : hints) {
                if (hint != null && hint.toUpperCase(Locale.ROOT).contains("EUR")) return "EUR";
            }
        }
        return "USD";
    }

    private BigDecimal currentFxRate(String currency) {
        try {
            var latest = marketFxService.getTcmbLatestRates(currency);
            if (latest == null || latest.getRates() == null) return null;
            var rate = latest.getRates().stream()
                    .filter(r -> currency.equalsIgnoreCase(r.getSymbol()))
                    .findFirst().orElse(null);
            if (rate == null) return null;
            BigDecimal sell = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (sell == null) return null;
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1
                    ? sell.divide(BigDecimal.valueOf(unit), 8, RoundingMode.HALF_UP)
                    : sell;
        } catch (Exception ex) {
            log.debug("FX rate snapshot failed for {}: {}", currency, ex.getMessage());
            return null;
        }
    }

    private BigDecimal currentGoldGramTry() {
        try {
            var spot = goldMarketService.getSpotGold();
            if (spot != null) {
                if (spot.getOfficialPureGoldGramTry() != null) return spot.getOfficialPureGoldGramTry();
                if (spot.getGramCloseTry() != null) return spot.getGramCloseTry();
            }
        } catch (Exception ex) {
            log.debug("Gold gram TL snapshot failed: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Günlük gram altın TL serisi — altına dayalı bondlar ve eski tarihli işlem için.
     * BIST gold history (TRY range) → her gün kapanış. Pencereye kırpılır.
     */
    private NavigableMap<LocalDate, BigDecimal> loadGoldGramHistoryByDay(LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        try {
            GoldHistoryResponse hist = goldMarketService.getGoldHistory(metalRange(from), "TRY");
            if (hist == null || hist.getPoints() == null) return map;
            for (GoldHistoryPoint pt : hist.getPoints()) {
                if (pt.getDate() == null || pt.getClose() == null) continue;
                try {
                    LocalDate day = LocalDate.parse(pt.getDate().substring(0, 10));
                    PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, pt.getClose(), from, to);
                } catch (Exception ignored) { /* skip */ }
            }
        } catch (Exception ex) {
            log.debug("Gold history series failed: {}", ex.getMessage());
        }
        return map;
    }

    /**
     * Eurobond günlük TL kapanış serisi: BI kote (USD/EUR/JPY) × <b>o güne ait</b> TCMB satış kuru
     * (floor-fill; o günden öncesi yoksa en yakın). Kur tarihsel veri yoksa güncel kura düşer.
     * Portföy performans grafiği ve genel /price-history (karşılaştırma) için kullanılır.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchEurobond(String isin, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        EurobondDetail d = eurobondService.detail(isin);
        String currency = d != null && d.getCurrency() != null ? d.getCurrency().toUpperCase(Locale.ROOT) : "USD";

        List<EurobondChartPoint> pts = eurobondService.chart(isin, eurobondRange(from));
        if (pts == null || pts.isEmpty()) {
            return map;
        }

        NavigableMap<LocalDate, BigDecimal> fxByDay = loadFxHistoryPerUnit(currency, from.minusDays(10), to);
        BigDecimal latestFx = d != null && d.getFxRate() != null
                ? d.getFxRate() : eurobondService.fxRateToTry(currency);
        if (fxByDay.isEmpty() && (latestFx == null || latestFx.signum() <= 0)) {
            return map;
        }

        for (EurobondChartPoint p : pts) {
            if (p == null || p.date() == null || p.close() == null) {
                continue;
            }
            LocalDate day;
            try {
                day = LocalDate.parse(p.date().substring(0, Math.min(10, p.date().length())));
            } catch (Exception e) {
                continue;
            }
            BigDecimal fx = null;
            var fl = fxByDay.floorEntry(day);
            if (fl != null) {
                fx = fl.getValue();
            } else if (!fxByDay.isEmpty()) {
                fx = fxByDay.firstEntry().getValue();
            }
            if (fx == null || fx.signum() <= 0) {
                fx = latestFx;
            }
            if (fx == null || fx.signum() <= 0) {
                continue;
            }
            BigDecimal tryClose = p.close().multiply(fx).setScale(6, RoundingMode.HALF_UP);
            PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, tryClose, from, to);
        }
        return map;
    }

    /** Verilen döviz için günlük TCMB satış kuru serisi (birim>1 ise bölünür; örn. JPY). */
    private NavigableMap<LocalDate, BigDecimal> loadFxHistoryPerUnit(String currency, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return map;
        }
        String cur = currency.toUpperCase(Locale.ROOT);
        int unit = 1;
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates(cur);
            if (latest != null && latest.getRates() != null) {
                FxRateItem rate = latest.getRates().stream()
                        .filter(r -> cur.equalsIgnoreCase(r.getSymbol()))
                        .findFirst().orElse(null);
                if (rate != null && rate.getUnit() > 1) {
                    unit = rate.getUnit();
                }
            }
        } catch (Exception ignored) {
            // birim 1 varsay
        }
        BigDecimal unitBd = BigDecimal.valueOf(unit);
        try {
            List<FxHistoryPoint> pts = tcmbFxHistoryPort.fetchHistory(cur, from, to);
            for (FxHistoryPoint p : pts) {
                if (p.getDate() == null || p.getClose() == null) {
                    continue;
                }
                try {
                    LocalDate day = LocalDate.parse(p.getDate().substring(0, 10));
                    BigDecimal close = unit > 1 ? p.getClose().divide(unitBd, 8, RoundingMode.HALF_UP) : p.getClose();
                    map.put(day, close);
                } catch (Exception ignored) {
                    // skip
                }
            }
        } catch (Exception e) {
            log.debug("Eurobond FX history unavailable for {}: {}", cur, e.getMessage());
        }
        return map;
    }

    /** BI eurobond grafik aralığı — istenen {@code from}'u kapsayacak en kısa range. */
    private static String eurobondRange(LocalDate from) {
        long d = daysSince(from);
        if (d <= 31) return "1M";
        if (d <= 95) return "3M";
        if (d <= 185) return "6M";
        if (d <= 370) return "1Y";
        if (d <= 1100) return "3Y";
        if (d <= 1830) return "5Y";
        return "MAX";
    }

    private NavigableMap<LocalDate, BigDecimal> fetchGold(String symbol, LocalDate from, LocalDate to) {
        String upper = symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();

        if ("GOLD".equals(upper)) {
            // ONS altın: USD kapanışı × o güne ait TCMB USD/TRY satış kuru.
            // ÖNCEKİ DAVRANIŞ: tüm geçmiş USD'ler GÜNCEL USD/TRY ile çarpılıyordu →
            // eski tarih price-at'i bugünkü kurla şişirilmiş TL veriyordu (ör. geçen
            // sene bugünkü USD altın × bugünkü kur = yanlış TL).
            GoldHistoryResponse hist = goldMarketService.getGoldHistory(metalRange(from), "USD");
            if (hist == null || hist.getPoints() == null) {
                return map;
            }
            NavigableMap<LocalDate, BigDecimal> usdTryByDay =
                    CommodityHistoricalTryConversion.loadUsdTryHistory(
                            tcmbFxHistoryPort, from.minusDays(10), LocalDate.now());
            BigDecimal latestUsdTry = fetchUsdTryRate();
            BigDecimal earliestUsdTry = usdTryByDay.isEmpty() ? null : usdTryByDay.firstEntry().getValue();
            if (usdTryByDay.isEmpty() && latestUsdTry == null) {
                return map;
            }
            for (GoldHistoryPoint pt : hist.getPoints()) {
                if (pt.getDate() == null || pt.getClose() == null) {
                    continue;
                }
                try {
                    LocalDate day = LocalDate.parse(pt.getDate().substring(0, 10));
                    BigDecimal fallbackRate = earliestUsdTry != null ? earliestUsdTry : latestUsdTry;
                    BigDecimal tryPrice = CommodityHistoricalTryConversion.convertUsdCloseToTry(
                            pt.getClose(), day, usdTryByDay, fallbackRate);
                    if (tryPrice != null) {
                        PortfolioHistoricalPriceSeriesSupport.putIfInRange(map, day, tryPrice, from, to);
                    }
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
        if (isPlatinumOrPalladiumSymbol(symbol)) {
            return fetchPreciousMetal(symbol, from, to);
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
        // (price-at'i 80 sn bloke edebiliyor). Önce Binance BTCTRY/ETHTRY günlük (frontend ile aynı
        // kaynak — günlük hassasiyet, FX dönüşüm yok); pair yoksa veya from Binance listeleme
        // tarihinden öncesi ise Yahoo USD × TCMB USD/TRY fallback'ine düşeriz.
        if (daysSince(from) > 366) {
            String baseSym = (item != null && item.getSymbol() != null && !item.getSymbol().isBlank())
                    ? item.getSymbol() : symbol;

            // 1) Binance TRY direkt (5y / max ikisi de günlük) — pair coin'in gerçek TRY
            //    çiftiyse ratio check otomatik 1'e yakın olur.
            NavigableMap<LocalDate, BigDecimal> binance = fetchCryptoBinanceTry(baseSym, from);
            if (!binance.isEmpty()) {
                LocalDate oldest = binance.firstKey();
                // İki durumu ayır:
                // (a) Binance pair'i `from`'u kapsıyorsa (oldest ≤ from): tüm pencere içinde
                //     güvenle kullan — chart ya da price-at fark etmez.
                // (b) Aksi durumda window dar (≤ 60 gün) ve Binance'in `to`'ya kadar verisi
                //     varsa (floorEntry(to) != null), bu büyük olasılıkla price-at sorgusu →
                //     Binance günlük precision'a izin ver. Geniş window'larda (chart) eski
                //     veri için Yahoo'ya düş.
                boolean coversFrom = !oldest.isAfter(from);
                boolean priceAtLikeWindow = ChronoUnit.DAYS.between(from, to) <= 60
                        && binance.floorEntry(to) != null;
                if (coversFrom || priceAtLikeWindow) {
                    return binance;
                }
                log.debug("Binance {}TRY series starts {} but from={} — Yahoo fallback'e geç", baseSym, oldest, from);
            }

            // 2) Yahoo USD × TCMB fallback
            NavigableMap<LocalDate, BigDecimal> usdDerived = fetchCryptoUsdFxFallback(baseSym, from, to);
            // Yahoo ticker çakışması koruması: türetilen güncel TL değeri coin'in gerçek güncel TRY
            // fiyatından çok uzaksa (ör. HYPE-USD = farklı/ölü bir token) → yanlış token.
            boolean tokenMismatch = false;
            if (!usdDerived.isEmpty() && item != null && item.getCurrentPrice() != null
                    && item.getCurrentPrice().signum() > 0) {
                double ratio = usdDerived.lastEntry().getValue().doubleValue()
                        / item.getCurrentPrice().doubleValue();
                if (ratio < 0.2 || ratio > 5.0) {
                    log.debug("Crypto Yahoo fallback {} reddedildi (oran {} — yanlış token olabilir)", symbol, ratio);
                    tokenMismatch = true;
                }
            }
            // Dar pencere (≤60 gün = price-at): Yahoo sonucunu olduğu gibi döndür. CoinGecko ~1 yıl ile
            // sınırlı olduğundan eski tarihli price-at'te yanlış (güncel) fiyat vermesin diye CoinGecko'ya DÜŞME.
            if (ChronoUnit.DAYS.between(from, to) <= 60) {
                return tokenMismatch ? PortfolioHistoricalPriceSeriesSupport.emptyMap() : usdDerived;
            }
            // Geniş pencere (grafik): Yahoo'da ≥2 nokta + doğru token varsa kullan. Yoksa coin'in Binance/
            // Yahoo'da uzun geçmişi yok (çok küçük/yeni coin) → CoinGecko'nun ~1 yıllık TRY serisine düş
            // (5Y/Tüm istense bile elde edilebilir TÜM geçmişi ~1 yıl olabilir → 1 nokta yerine onu göster;
            // rebase common-start kıyası adil tutar). Aşağıdaki CoinGecko bloğuna düşülür.
            if (!tokenMismatch && usdDerived.size() >= 2) {
                return usdDerived;
            }
        }

        // Yakın tarih (≤1 yıl): önce CoinGecko TRY
        NavigableMap<LocalDate, BigDecimal> coinGecko = PortfolioHistoricalPriceSeriesSupport.emptyMap();
        if (item != null && item.getId() != null) {
            try {
                coinGecko = PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(
                        cryptoMarketService.getMarketChart(item.getId(), cryptoDays(from), "try", null, null));
            } catch (Exception e) {
                log.debug("CoinGecko market chart failed for {}: {}", symbol, e.getMessage());
            }
        }
        if (coinGecko != null && !coinGecko.isEmpty()) {
            return coinGecko;
        }

        // CoinGecko boş/429 → kısa vadede de Yahoo USD×TCMB kuru fallback'ine düş, istenen pencereye kırp.
        String baseSym = (item != null && item.getSymbol() != null && !item.getSymbol().isBlank())
                ? item.getSymbol() : symbol;
        NavigableMap<LocalDate, BigDecimal> usdDerived = fetchCryptoUsdFxFallback(baseSym, from, to);
        if (usdDerived.isEmpty()) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        // Yanlış token koruması (HYPE-USD gibi çakışmalar)
        if (item != null && item.getCurrentPrice() != null && item.getCurrentPrice().signum() > 0) {
            double ratio = usdDerived.lastEntry().getValue().doubleValue() / item.getCurrentPrice().doubleValue();
            if (ratio < 0.2 || ratio > 5.0) {
                log.debug("Crypto Yahoo fallback {} reddedildi (oran {})", symbol, ratio);
                return PortfolioHistoricalPriceSeriesSupport.emptyMap();
            }
        }
        // İstenen [from, to] penceresine kırp (fallback 5y/haftalık dönebiliyor).
        NavigableMap<LocalDate, BigDecimal> windowed = new TreeMap<>();
        usdDerived.forEach((day, v) -> {
            if (!day.isBefore(from) && !day.isAfter(to)) {
                windowed.put(day, v);
            }
        });
        return windowed.isEmpty() ? usdDerived : windowed;
    }

    /**
     * Kripto eski tarihler için TL serisi (öncelikli): Binance Spot {BASE}TRY günlük (5y) ya da
     * haftalık (max) kapanışları. Frontend 5Y/Tüm grafikleriyle aynı kaynak — FX dönüşümü yok.
     * Coin'in TRY pair'i Binance'te listelenmemişse veya istenen tarih listeleme öncesi ise
     * boş döner; çağıran kod Yahoo fallback'ine geçer.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchCryptoBinanceTry(String baseSymbol, LocalDate from) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        if (baseSymbol == null || baseSymbol.isBlank()) {
            return out;
        }
        // 5y günlük serisi 1830 güne kadarki tarihleri kapsar; daha eski talepler için max (haftalık).
        String range = daysSince(from) <= 1830 ? "5y" : "max";
        List<CryptoChartCandle> candles;
        try {
            candles = cryptoBinanceChartService.getChartCandles(baseSymbol, range, "try");
        } catch (Exception e) {
            log.debug("Binance TRY chart failed for {}: {}", baseSymbol, e.getMessage());
            return out;
        }
        if (candles == null || candles.isEmpty()) {
            return out;
        }
        ZoneId istanbul = ZoneId.of("Europe/Istanbul");
        for (CryptoChartCandle c : candles) {
            if (c.getClose() == null || c.getClose().signum() <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(c.getTimestamp()).atZone(istanbul).toLocalDate();
            out.put(day, c.getClose());
        }
        return out;
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

        // NOT: TCMB pencereyi bugüne kadar yüklüyoruz, sadece [from, to]'ya değil. Çağıran kod
        // fetchCrypto'da haritanın SON noktasını (= bugünkü Yahoo USD × kur) CoinGecko'nun güncel
        // TRY fiyatıyla karşılaştırıp ratio sanity-check yapıyor. Eğer TCMB'yi `to`'ya kadar
        // yüklersek, eski from (ör. 2015) için bugünkü kur kayıt edilmez → son nokta eski kurla
        // çarpılır → BTC için ratio ~0.08 çıkıp Yahoo fallback komple reddedilir.
        NavigableMap<LocalDate, BigDecimal> usdTryByDay =
                CommodityHistoricalTryConversion.loadUsdTryHistory(
                        tcmbFxHistoryPort, from.minusDays(10), LocalDate.now());
        BigDecimal latestUsdTry = fetchUsdTryRate();
        // Kur o günden öncesine ait değilse en eski mevcut kuru kullan (eski coin günleri TCMB
        // geçmişinin başından da eski olabilir).
        BigDecimal earliestUsdTry = usdTryByDay.isEmpty() ? null : usdTryByDay.firstEntry().getValue();
        if (usdTryByDay.isEmpty() && latestUsdTry == null) {
            return out;
        }

        // NOT: Burada [from, to] penceresine KIRPMIYORUZ. Çağıran kod (fetchCrypto) ratio
        // sanity-check yaparken haritanın son noktasını CoinGecko'nun güncel TRY fiyatıyla
        // karşılaştırıyor. Eğer burada `to`'ya göre kırpsaydık, eski tarihli price-at
        // sorgularında son nokta 2021 değeri olup ratio ~0 çıkıp Yahoo fallback toptan
        // reddediliyordu. Pencereleme caller'ın price-at floorEntry'siyle veya >1y kolunda
        // doğrudan tüketimle yapılır.
        for (Map.Entry<LocalDate, BigDecimal> e : usdByDay.entrySet()) {
            LocalDate day = e.getKey();
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

    /**
     * BIST platin/paladyum: portföyde {@code PLATINUM:GRAM_TRY} / {@code PALLADIUM:USD_ONS} vb.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchPreciousMetal(String symbol, LocalDate from, LocalDate to) {
        String upper = symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        PreciousMetalType type = upper.startsWith("PLATINUM") ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM;
        String cat = "GRAM_TRY";
        if (upper.contains(":")) {
            String[] parts = upper.split(":", 2);
            if (parts.length > 1 && !parts[1].isBlank()) {
                cat = parts[1].trim();
            }
        }
        String currency = cat.contains("USD") ? "USD" : cat.contains("EUR") ? "EUR" : "TRY";

        PreciousMetalHistoryResponse hist;
        try {
            hist = preciousMetalService.getHistory(type, metalRange(from), currency);
        } catch (Exception e) {
            log.debug("Precious metal history failed for {}: {}", symbol, e.getMessage());
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }
        if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
            return PortfolioHistoricalPriceSeriesSupport.emptyMap();
        }

        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (PreciousMetalHistoryPoint pt : hist.getPoints()) {
            if (pt.getDate() == null) {
                continue;
            }
            BigDecimal price = resolvePreciousClose(pt, cat);
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

    private static BigDecimal resolvePreciousClose(PreciousMetalHistoryPoint pt, String cat) {
        BigDecimal v = switch (cat) {
            case "KG_TRY" -> pt.getTryKg();
            case "USD_ONS" -> pt.getUsdOns();
            case "EUR_ONS" -> pt.getEurOns();
            default -> pt.getTryGram();
        };
        return v != null ? v : pt.getValue();
    }

    static boolean isPlatinumOrPalladiumSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("PLATINUM") || upper.startsWith("PALLADIUM");
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

    /** Yahoo hisse aralığı: 1y / 2y / 5y / 10y. "max" KULLANMIYORUZ: Yahoo max+1d
     *  uzun geçmişli hisseler için sessizce aylık döner (ASELS max+1d → 313 nokta).
     *  10y günlük tavandır; daha eski tarihler 10y+1d içindeki son geçerli güne floorEntry. */
    private static String yahooStockRange(LocalDate from) {
        long d = daysSince(from);
        if (d <= 370) return "1y";
        if (d <= 730) return "2y";
        if (d <= 1830) return "5y";
        return "10y";
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
        // CoinGecko ÜCRETSIZ market_chart en fazla 365 gün verir; daha fazlası (ör. 1Y için "370")
        // 401/boş döner → seri gelmez, çağıran Yahoo'ya düşer (obscure coin'de 1 noktaya iner).
        // Kısa-vade CoinGecko yolu en çok ~1 yıl ister → her zaman tam tavan: 365 gün.
        return "365";
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
