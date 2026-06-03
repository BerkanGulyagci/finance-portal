package com.finance.portal.market.application.stock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import com.finance.portal.market.application.stock.port.MidasStockPort;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class StockQueryService {

    private static final Logger logger = LoggerFactory.getLogger(StockQueryService.class);
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

    /** Hızlı (fiyat/intraday/liste/grafik) veri için LKG ömrü — kaynak çökerse en fazla bu kadar eski veri gösterilir. */
    private static final Duration STOCK_LKG_TTL = Duration.ofDays(3);

    private final YahooStockPort yahooStockPort;
    private final StockSymbolProvider stockSymbolProvider;
    private final MidasStockPort midasStockPort;
    private final LastKnownGoodCache lkg;

    public StockQueryService(YahooStockPort yahooStockPort,
                             StockSymbolProvider stockSymbolProvider,
                             MidasStockPort midasStockPort,
                             LastKnownGoodCache lkg) {
        this.yahooStockPort = yahooStockPort;
        this.stockSymbolProvider = stockSymbolProvider;
        this.midasStockPort = midasStockPort;
        this.lkg = lkg;
    }

    public StockSummary getStockSummary(String symbol) {
        YahooStockMeta meta = fetchMetaOrThrow(symbol);
        return mapToStockSummary(meta);
    }

    /**
     * Verilen sembollerin özetlerini paralel toplar — endeks listesi (XU100.IS…) ve endeks bileşen
     * hisseleri için yeniden kullanılır. Tek tek başarısızlığa ayrı katlanır (biri çökerse diğerleri gelir).
     */
    public List<StockSummary> getSummariesFor(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return List.of();
        return fetchSummariesInParallel(symbols);
    }

    @Cacheable(cacheNames = "market.stocks.detail", key = "'detail:' + #symbol")
    public StockDetail getStockDetail(String symbol) {
        YahooStockMeta meta = fetchMetaOrThrow(symbol);
        StockSummary summary = mapToStockSummary(meta);

        StockDetail detail = new StockDetail();
        detail.setSymbol(meta.getSymbol());
        detail.setName(meta.getLongName());
        detail.setCurrency(meta.getCurrency());
        detail.setExchange(meta.getExchangeName());
        detail.setSummary(summary);
        detail.setFiftyTwoWeekHigh(meta.getFiftyTwoWeekHigh());
        detail.setFiftyTwoWeekLow(meta.getFiftyTwoWeekLow());
        return detail;
    }

    @Cacheable(cacheNames = "market.stocks.page", key = "'page:' + #page + ':size:' + #size")
    public StockPageResponse getPagedStockSummaries(int page, int size) {
        return fetchPage(stockSymbolProvider.getPagedSymbols(page, size),
                stockSymbolProvider.getTotalElements(), page, size);
    }

    @Cacheable(cacheNames = "market.stocks.page", key = "'index:' + #index + ':page:' + #page + ':size:' + #size")
    public StockPageResponse getPagedStockSummariesByIndex(int page, int size, String index) {
        List<String> allSymbols = switch (index) {
            case "BIST30" -> stockSymbolProvider.getBist30Symbols();
            case "BIST50" -> stockSymbolProvider.getBist50Symbols();
            case "BIST100" -> stockSymbolProvider.getBist100Symbols();
            default -> stockSymbolProvider.getAllSymbols();
        };
        int total = allSymbols.size();
        int start = page * size;
        List<String> paged = start >= total ? List.of()
                : allSymbols.subList(start, Math.min(start + size, total));
        return fetchPage(paged, total, page, size);
    }

    private StockPageResponse fetchPage(List<String> symbols, int totalElements, int page, int size) {
        List<StockSummary> content = symbols.isEmpty() ? new ArrayList<>() : fetchSummariesInParallel(symbols);
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        StockPageResponse response = new StockPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        return response;
    }

    /** {@code symbols}'in özetlerini 5 thread'lik havuzda paralel toplar; başarısızlığa ayrı katlanır. */
    private List<StockSummary> fetchSummariesInParallel(List<String> symbols) {
        List<StockSummary> content = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(5)) {
            List<Future<StockSummary>> futures = submitSummaryFutures(executor, symbols);
            collectSummaryFutures(futures, content);
        }
        return content;
    }

    private List<Future<StockSummary>> submitSummaryFutures(java.util.concurrent.ExecutorService executor,
                                                            List<String> symbols) {
        return symbols.stream()
                .map(symbol -> executor.submit(() -> safeFetchSummary(symbol)))
                .toList();
    }

    private StockSummary safeFetchSummary(String symbol) {
        try {
            return getStockSummary(symbol);
        } catch (Exception ex) {
            logger.warn("Failed to fetch stock summary for {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    private void collectSummaryFutures(List<Future<StockSummary>> futures, List<StockSummary> sink) {
        for (Future<StockSummary> f : futures) {
            try {
                StockSummary s = f.get(5, TimeUnit.SECONDS);
                if (s != null) {
                    sink.add(s);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while collecting stock summaries");
                return;
            } catch (Exception ex) {
                logger.warn("Stock summary future failed: {}", ex.getMessage());
            }
        }
    }

    public StockChartResponse getStockChart(String symbol) {
        return toChartResponse(symbol, yahooStockPort.fetchChart(symbol));
    }

    @Cacheable(cacheNames = "market.stocks.midas", key = "'midas:' + #symbol")
    public MidasStockDetail getMidasDetail(String symbol) {
        // getmidas detay scrape'i (warm-up yok) çökerse/boş dönerse son başarılı LKG'yi servis et.
        return lkg.resilient("stock.midas.detail:" + symbol, STOCK_LKG_TTL,
                new TypeReference<MidasStockDetail>() {}, () -> midasStockPort.fetchDetail(symbol));
    }

    @Cacheable(cacheNames = "market.stocks.chart", key = "#symbol + ':' + #range + ':' + #interval")
    public StockChartResponse getStockChartWithParams(String symbol, String range, String interval) {
        String[] normalized = normalizeStockChartRange(range, interval);
        YahooChartSnapshot snapshot = fetchChartSnapshotResilient(symbol, normalized[0], normalized[1]);
        return toChartResponse(symbol, snapshot);
    }

    public List<Map<String, Object>> getStockOhlc(String symbol, String range, String interval) {
        String[] normalized = normalizeStockChartRange(range, interval);
        YahooChartSnapshot snapshot = fetchChartSnapshotResilient(symbol, normalized[0], normalized[1]);
        List<Long> timestamps = snapshot.getTimestamps();
        YahooQuoteSeries quote = snapshot.getQuote();
        if (timestamps == null || quote == null) {
            throw new ResourceNotFoundException("OHLC data not found for symbol: " + symbol);
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
            Long vol = safeGetLong(quote.getVolume(), i);
            candle.put("volume", vol);
            data.add(candle);
        }
        if (data.isEmpty()) {
            throw new ResourceNotFoundException("OHLC data not found for symbol: " + symbol);
        }
        return data;
    }

    /**
     * Yahoo grafik snapshot'ını LKG dayanıklılığı + SAATLİK fallback ile çeker.
     *
     * <p><b>Neden saatlik fallback:</b> Yahoo çoğu BIST endeksi için (XU050, XUTUM, XGIDA, XK100 ve
     * tüm sektör endeksleri…) GÜNLÜK/haftalık/aylık bar TUTMAZ — yalnız intraday (5m/1h) verir
     * ({@code firstTradeDate=null}). Bu sembollerde {@code 1d/1wk/1mo} isteği tek nokta döndürür ve
     * grafik boş/kırık çizilir (BIST 50'nin 1Y/5Y/Tüm'de gelmemesinin sebebi buydu). Sadece XU100/
     * XU030/XBANK/XUSIN gibi birkaçında gerçek günlük geçmiş var.</p>
     *
     * <p>Çözüm: günlük+ bir istek &lt;2 nokta dönerse {@code 1h} ile yeniden dene. Yahoo saatlik veriyi
     * ~730 güne kadar veriyor (XU050 730d/1h → ~6250 nokta), bu yüzden uzun aralıklar (5Y/Tüm) bu
     * sembollerde en fazla ~2 yıl gösterir — ama boş yerine GERÇEK veri gelir. Günlük geçmişi olan
     * semboller etkilenmez (≥2 nokta → fallback tetiklenmez).</p>
     */
    private YahooChartSnapshot fetchChartSnapshotResilient(String symbol, String range, String interval) {
        YahooChartSnapshot snapshot = lkg.resilient(
                "stock.chart:" + symbol + ":" + range + ":" + interval, STOCK_LKG_TTL,
                new TypeReference<YahooChartSnapshot>() {},
                () -> yahooStockPort.fetchChartWithParams(symbol, range, interval));

        if (isIntradayInterval(interval) || nonNullCloseCount(snapshot) >= 2) {
            return snapshot; // intraday zaten saatlik/altı, ya da günlük geçmiş mevcut → fallback gerekmez
        }

        // Günlük+ istek dejenere (≤1 nokta) → saatlik fallback. Yahoo saatlik ~730g sınırı için range'i 2y'ye kıs.
        String fbRange = isLongRange(range) ? "2y" : range;
        logger.info("Chart degenerate for {} {}/{} ({} pts) → hourly fallback {}/1h",
                symbol, range, interval, nonNullCloseCount(snapshot), fbRange);
        try {
            YahooChartSnapshot hourly = lkg.resilient(
                    "stock.chart:" + symbol + ":" + fbRange + ":1h", STOCK_LKG_TTL,
                    new TypeReference<YahooChartSnapshot>() {},
                    () -> yahooStockPort.fetchChartWithParams(symbol, fbRange, "1h"));
            return nonNullCloseCount(hourly) >= 2 ? hourly : snapshot;
        } catch (RuntimeException ex) {
            logger.warn("Hourly fallback failed for {} {}: {}", symbol, fbRange, ex.getMessage());
            return snapshot;
        }
    }

    /** {@code 1m,5m,15m,30m,90m,1h} → true; {@code 1d,1wk,1mo} → false (matches() tam eşleşme ister, "1mo" eşleşmez). */
    private static boolean isIntradayInterval(String interval) {
        return interval != null && interval.toLowerCase(Locale.ROOT).matches("\\d+[mh]");
    }

    /** Yahoo saatlik 730g sınırını aşan aralıklar — saatlik fallback'te 2y'ye kısılır. */
    private static boolean isLongRange(String range) {
        if (range == null) return false;
        String r = range.toLowerCase(Locale.ROOT);
        return r.equals("5y") || r.equals("10y") || r.equals("30y") || r.equals("max") || r.equals("all");
    }

    private static int nonNullCloseCount(YahooChartSnapshot snapshot) {
        if (snapshot == null || snapshot.getQuote() == null || snapshot.getQuote().getClose() == null) {
            return 0;
        }
        return (int) snapshot.getQuote().getClose().stream().filter(Objects::nonNull).count();
    }

    /**
     * Frontend "Tüm" düğmesi range=max ile gelirse: Yahoo "max" sessizce ~313 aylık-haftalık
     * noktaya düşürür (aynı sayı her interval'da). Bunun yerine 30y + 1d kullanıyoruz —
     * Yahoo bu kombinasyona uyar; hisse listing tarihinden (BIST 90'lar başı) bugüne kadar
     * tam günlük nokta döner. Örnek ASELS (listing 2000): 30y/1d → ~6300 günlük nokta.
     * Listing 30y'den eskiyse (nadir) o tarihten itibaren mevcut tüm günler döner.
     * 1mo/1wk frontend interval default'u günlüğe çıkarılır.
     */
    private static String[] normalizeStockChartRange(String range, String interval) {
        String r = range == null ? "" : range.trim().toLowerCase(java.util.Locale.ROOT);
        String i = interval == null ? "" : interval.trim().toLowerCase(java.util.Locale.ROOT);
        boolean isAllAlias = "max".equals(r) || "all".equals(r) || "tum".equals(r) || "tüm".equals(r);
        if (isAllAlias) {
            return new String[]{"30y", "1d"};
        }
        return new String[]{
                range == null || range.isBlank() ? "1d" : range,
                interval == null || interval.isBlank() ? "1m" : interval
        };
    }

    private StockChartResponse toChartResponse(String symbol, YahooChartSnapshot snapshot) {
        if (snapshot == null) {
            logger.info("Stock chart snapshot null for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock chart data not available for symbol: " + symbol);
        }
        List<Long> timestamps = snapshot.getTimestamps();
        List<BigDecimal> closes = snapshot.getQuote() != null ? snapshot.getQuote().getClose() : null;

        if (timestamps == null || closes == null || timestamps.isEmpty() || closes.isEmpty()) {
            logger.info("Stock chart data missing for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock chart data not available for symbol: " + symbol);
        }

        int size = Math.min(timestamps.size(), closes.size());
        List<Long> filteredTimestamps = java.util.stream.IntStream.range(0, size)
                .filter(i -> timestamps.get(i) != null && closes.get(i) != null)
                .mapToObj(timestamps::get)
                .toList();
        List<BigDecimal> filteredCloses = java.util.stream.IntStream.range(0, size)
                .filter(i -> timestamps.get(i) != null && closes.get(i) != null)
                .mapToObj(closes::get)
                .toList();

        StockChartResponse chartResponse = new StockChartResponse();
        chartResponse.setSymbol(symbol);
        chartResponse.setTimestamps(filteredTimestamps);
        chartResponse.setClosePrices(filteredCloses);
        return chartResponse;
    }

    private BigDecimal safeGet(List<BigDecimal> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private Long safeGetLong(List<Long> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private YahooStockMeta fetchMetaOrThrow(String symbol) {
        // Yahoo meta/quote çekimi (detail + liste özetleri buradan akar) çökerse/boş dönerse
        // son başarılı snapshot'ı servis et — warm-up'ı olmayan detay için kritik.
        YahooChartSnapshot snapshot = lkg.resilient("stock.yahoo.chart:" + symbol, STOCK_LKG_TTL,
                new TypeReference<YahooChartSnapshot>() {}, () -> yahooStockPort.fetchChart(symbol));
        if (!snapshot.hasMeta()) {
            logger.info("Stock not found for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock not found for symbol: " + symbol);
        }
        return snapshot.getMeta();
    }

    private StockSummary mapToStockSummary(YahooStockMeta meta) {
        StockSummary summary = new StockSummary();
        summary.setSymbol(meta.getSymbol());
        summary.setName(meta.getLongName());
        summary.setCurrency(meta.getCurrency());
        summary.setExchange(meta.getExchangeName());

        BigDecimal price = defaultBigDecimal(meta.getRegularMarketPrice());
        BigDecimal previousClose = defaultBigDecimal(meta.getPreviousClose());
        BigDecimal change = price.subtract(previousClose);
        BigDecimal changePercent = BigDecimal.ZERO;

        if (previousClose.compareTo(BigDecimal.ZERO) != 0) {
            changePercent = change
                    .divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        summary.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        summary.setChange(change.setScale(2, RoundingMode.HALF_UP));
        summary.setChangePercent(changePercent);
        summary.setDayHigh(scaleIfNotNull(meta.getRegularMarketDayHigh()));
        summary.setDayLow(scaleIfNotNull(meta.getRegularMarketDayLow()));
        summary.setVolume(meta.getRegularMarketVolume());
        summary.setAsOf(formatAsOf(meta.getRegularMarketTime()));
        return summary;
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scaleIfNotNull(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private String formatAsOf(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ISTANBUL_ZONE);
        return dateTime.toString();
    }
}
