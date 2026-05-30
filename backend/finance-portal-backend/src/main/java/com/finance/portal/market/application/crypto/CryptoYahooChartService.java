package com.finance.portal.market.application.crypto;

import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kripto 5Y / Tüm grafikleri — USD ve EUR için Yahoo Finance OHLC (BTC-USD, BTC-EUR).
 */
@Service
public class CryptoYahooChartService {

    private static final Logger log = LoggerFactory.getLogger(CryptoYahooChartService.class);

    private final YahooStockPort yahooStockPort;
    private final CryptoMarketService cryptoMarketService;

    /** Çözümlenmiş Yahoo sembol tabanları (ticker → "USDG33793" gibi). Eşleme stabil → kalıcı in-memory cache. */
    private final ConcurrentHashMap<String, String> resolvedYahooBase = new ConcurrentHashMap<>();

    public CryptoYahooChartService(YahooStockPort yahooStockPort,
                                   CryptoMarketService cryptoMarketService) {
        this.yahooStockPort = yahooStockPort;
        this.cryptoMarketService = cryptoMarketService;
    }

    /**
     * Bir kripto ticker'ı için DOĞRU Yahoo sembol tabanını çözer. Yahoo, ticker çakışmalarında
     * sembol sonuna sayısal ID ekler (ör. {@code USDG} → {@code USDG33793}, {@code MNT} → {@code MNT27075});
     * naif {@code {TICKER}-USD} yanlış/uyumsuz bir kıymete gidebiliyor (ör. USDG-USD = $5.49, gerçek $1).
     * Coin İSMİYLE (CoinGecko'dan) Yahoo araması yapıp ticker-prefix'i eşleşen CRYPTOCURRENCY sembolünü
     * seçer. Bulunamaz/hata → ticker'ın kendisi (eski davranış; BTC/ETH/DAI gibi çakışmasız coinler etkilenmez).
     * Başarılı çözüm kalıcı in-memory cache'lenir.
     */
    String resolveYahooBase(String baseSymbol) {
        if (baseSymbol == null || baseSymbol.isBlank()) {
            return baseSymbol;
        }
        String key = baseSymbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (key.isEmpty()) {
            return baseSymbol;
        }
        String cached = resolvedYahooBase.get(key);
        if (cached != null) {
            return cached;
        }
        // Coin adını çöz — Yahoo ticker araması döviz çiftlerine takıldığından isimle aramak gerekir.
        String query = key;
        try {
            CryptoMarketItem item = cryptoMarketService.findBySymbol(key);
            if (item != null && item.getName() != null && !item.getName().isBlank()) {
                query = item.getName();
            }
        } catch (Exception ignore) {
            // CoinGecko'da bulunamadı → ticker ile dene
        }
        List<String> symbols;
        try {
            symbols = yahooStockPort.searchCryptoUsdSymbols(query);
        } catch (Exception e) {
            return key; // geçici hata → ticker'a düş, CACHE'LEME (sonra tekrar denenir)
        }
        if (symbols != null) {
            for (String s : symbols) {
                String full = s.toUpperCase(Locale.ROOT);
                if (!full.endsWith("-USD")) {
                    continue;
                }
                String resolvedBase = full.substring(0, full.length() - 4); // "-USD" ekini at
                String prefix = resolvedBase.replaceAll("[0-9]+$", "");       // sondaki sayısal ID'yi at
                if (prefix.equals(key)) {
                    resolvedYahooBase.put(key, resolvedBase);
                    if (!resolvedBase.equals(key)) {
                        log.info("Yahoo kripto sembolü çözümlendi: {} -> {}", key, resolvedBase);
                    }
                    return resolvedBase;
                }
            }
        }
        // Yahoo yanıt verdi ama sayısal-sonekli eşleşme yok → {TICKER}-USD doğru kabul edilir (cache'le).
        resolvedYahooBase.put(key, key);
        return key;
    }

    public static boolean shouldUseYahoo(String currency, String range) {
        if (currency == null || range == null) {
            return false;
        }
        String cur = currency.trim().toLowerCase(Locale.ROOT);
        if (!"usd".equals(cur) && !"eur".equals(cur)) {
            return false;
        }
        String r = CryptoBinanceChartService.normalizeRange(range);
        return "5y".equals(r) || "max".equals(r);
    }

    public static String toYahooSymbol(String baseSymbol, String currency) {
        if (baseSymbol == null || baseSymbol.isBlank()) {
            return null;
        }
        String base = baseSymbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            return null;
        }
        String cur = currency == null ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"USD".equals(cur) && !"EUR".equals(cur)) {
            cur = "USD";
        }
        return base + "-" + cur;
    }

    static String[] resolveYahooRangeInterval(String range) {
        String r = CryptoBinanceChartService.normalizeRange(range);
        // 5y / max için günlük (1d) — eski tarih price-at sorgularında haftalık/aylık
        // floorEntry kaymasını gidermek için. BTC max+1d ~4400 nokta, 5y+1d ~1825 nokta;
        // frontend kripto USD/EUR çizgisi için de daha yüksek çözünürlük (Binance TRY zaten günlük).
        return switch (r) {
            case "5y" -> new String[]{"5y", "1d"};
            case "max" -> new String[]{"max", "1d"};
            default -> null;
        };
    }

    @Cacheable(
            cacheNames = "market.crypto.yahoo.ohlc",
            key = "#baseSymbol + ':' + #range + ':' + #currency",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<Map<String, Object>> getOhlc(String baseSymbol, String range, String currency) {
        if (!shouldUseYahoo(currency, range)) {
            return List.of();
        }

        String[] params = resolveYahooRangeInterval(range);
        if (params == null) {
            return List.of();
        }

        List<String> quoteCurrencies = quoteCurrencyFallbackOrder(currency);
        for (String quote : quoteCurrencies) {
            String yahooSymbol = toYahooSymbol(resolveYahooBase(baseSymbol), quote);
            if (yahooSymbol == null) {
                continue;
            }
            try {
                List<Map<String, Object>> rows = fetchOhlcFromYahoo(yahooSymbol, params[0], params[1]);
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (Exception ex) {
                log.warn("Yahoo crypto OHLC failed for {} range={}: {}", yahooSymbol, range, ex.getMessage());
            }
        }

        log.warn("Yahoo crypto OHLC empty for base={} currency={} range={}", baseSymbol, currency, range);
        return List.of();
    }

    @Cacheable(
            cacheNames = "market.crypto.yahoo.chart",
            key = "#baseSymbol + ':' + #range + ':' + #currency",
            unless = "#result == null"
    )
    public StockChartResponse getLineChart(String baseSymbol, String range, String currency) {
        if (!shouldUseYahoo(currency, range)) {
            return null;
        }

        String[] params = resolveYahooRangeInterval(range);
        if (params == null) {
            return null;
        }

        List<String> quoteCurrencies = quoteCurrencyFallbackOrder(currency);
        for (String quote : quoteCurrencies) {
            String yahooSymbol = toYahooSymbol(resolveYahooBase(baseSymbol), quote);
            if (yahooSymbol == null) {
                continue;
            }
            try {
                StockChartResponse chart = fetchLineFromYahoo(yahooSymbol, params[0], params[1]);
                if (chart != null && chart.getTimestamps() != null && !chart.getTimestamps().isEmpty()) {
                    chart.setSymbol(yahooSymbol);
                    return chart;
                }
            } catch (Exception ex) {
                log.warn("Yahoo crypto line chart failed for {} range={}: {}", yahooSymbol, range, ex.getMessage());
            }
        }
        return null;
    }

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    /**
     * Kripto TL çizgi serisi (yalnız 5Y/Tüm): Yahoo USD kapanışları × aynı aralıktaki Yahoo
     * USD/TRY ({@code TRY=X}). CoinGecko TRY ~1 yıl, Binance {@code BTCTRY} ~2019 ile sınırlı
     * olduğundan "Tüm" TL çizgi grafiğinde eski (2014+) veriyi USD×kur ile türetir. Mum grafiğini
     * etkilemez. Kur o güne kadar forward-fill (floorEntry) edilir.
     */
    public StockChartResponse getTryLineViaUsd(String baseSymbol, String range) {
        if (!shouldUseYahoo("usd", range)) {
            return null; // sadece 5y / max
        }
        StockChartResponse usd = getLineChart(baseSymbol, range, "USD");
        if (usd == null || usd.getTimestamps() == null || usd.getTimestamps().isEmpty()
                || usd.getClosePrices() == null) {
            return null;
        }
        NavigableMap<LocalDate, BigDecimal> usdTryByDay = fetchUsdTryByDay(range);
        if (usdTryByDay.isEmpty()) {
            return null;
        }

        List<Long> ts = usd.getTimestamps();
        List<BigDecimal> usdCloses = usd.getClosePrices();
        int n = Math.min(ts.size(), usdCloses.size());
        List<Long> outTs = new ArrayList<>(n);
        List<BigDecimal> outCloses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Long epoch = ts.get(i);
            BigDecimal usdClose = usdCloses.get(i);
            if (epoch == null || usdClose == null || usdClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(epoch).atZone(ISTANBUL).toLocalDate();
            var rateEntry = usdTryByDay.floorEntry(day);
            BigDecimal rate = rateEntry != null ? rateEntry.getValue() : usdTryByDay.firstEntry().getValue();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            outTs.add(epoch);
            outCloses.add(usdClose.multiply(rate).setScale(6, RoundingMode.HALF_UP));
        }
        if (outTs.isEmpty()) {
            return null;
        }
        StockChartResponse out = new StockChartResponse();
        out.setSymbol(usd.getSymbol() != null ? usd.getSymbol() + "·TRY" : baseSymbol + "·TRY");
        out.setTimestamps(outTs);
        out.setClosePrices(outCloses);
        return out;
    }

    /** Yahoo USD/TRY ({@code TRY=X}) kapanışlarını gün→kur haritasına çevirir (aynı aralık/interval). */
    private NavigableMap<LocalDate, BigDecimal> fetchUsdTryByDay(String range) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        String[] params = resolveYahooRangeInterval(range);
        if (params == null) {
            return map;
        }
        StockChartResponse fx;
        try {
            fx = fetchLineFromYahoo("TRY=X", params[0], params[1]);
        } catch (Exception e) {
            log.warn("Yahoo USD/TRY (TRY=X) fetch failed range={}: {}", range, e.getMessage());
            return map;
        }
        if (fx == null || fx.getTimestamps() == null || fx.getClosePrices() == null) {
            return map;
        }
        List<Long> ts = fx.getTimestamps();
        List<BigDecimal> cl = fx.getClosePrices();
        int n = Math.min(ts.size(), cl.size());
        for (int i = 0; i < n; i++) {
            if (ts.get(i) == null || cl.get(i) == null || cl.get(i).compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(ts.get(i)).atZone(ISTANBUL).toLocalDate();
            map.put(day, cl.get(i));
        }
        return map;
    }

    private List<Map<String, Object>> fetchOhlcFromYahoo(String yahooSymbol, String yahooRange, String yahooInterval) {
        YahooChartSnapshot snapshot = yahooStockPort.fetchChartWithParams(yahooSymbol, yahooRange, yahooInterval);
        List<Long> timestamps = snapshot.getTimestamps();
        YahooQuoteSeries quote = snapshot.getQuote();
        if (timestamps == null || quote == null || timestamps.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal o = safeGet(quote.getOpen(), i);
            BigDecimal h = safeGet(quote.getHigh(), i);
            BigDecimal l = safeGet(quote.getLow(), i);
            BigDecimal c = safeGet(quote.getClose(), i);
            if (o == null || h == null || l == null || c == null) {
                continue;
            }
            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("time", timestamps.get(i));
            candle.put("open", o);
            candle.put("high", h);
            candle.put("low", l);
            candle.put("close", c);
            candle.put("volume", safeGetLong(quote.getVolume(), i));
            data.add(candle);
        }
        return data;
    }

    private StockChartResponse fetchLineFromYahoo(String yahooSymbol, String yahooRange, String yahooInterval) {
        YahooChartSnapshot snapshot = yahooStockPort.fetchChartWithParams(yahooSymbol, yahooRange, yahooInterval);
        List<Long> timestamps = snapshot.getTimestamps();
        List<BigDecimal> closes = snapshot.getQuote() != null ? snapshot.getQuote().getClose() : null;
        if (timestamps == null || closes == null || timestamps.isEmpty()) {
            return null;
        }

        int size = Math.min(timestamps.size(), closes.size());
        List<Long> filteredTimestamps = new ArrayList<>(size);
        List<BigDecimal> filteredCloses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (timestamps.get(i) != null && closes.get(i) != null) {
                filteredTimestamps.add(timestamps.get(i));
                filteredCloses.add(closes.get(i));
            }
        }
        if (filteredTimestamps.isEmpty()) {
            return null;
        }

        StockChartResponse response = new StockChartResponse();
        response.setSymbol(yahooSymbol);
        response.setTimestamps(filteredTimestamps);
        response.setClosePrices(filteredCloses);
        return response;
    }

    private static List<String> quoteCurrencyFallbackOrder(String currency) {
        String cur = currency == null ? "usd" : currency.trim().toLowerCase(Locale.ROOT);
        if ("eur".equals(cur)) {
            return List.of("EUR", "USD");
        }
        return List.of("USD");
    }

    private static BigDecimal safeGet(List<BigDecimal> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private static Long safeGetLong(List<Long> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }
}
