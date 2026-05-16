package com.finance.portal.market.application.stock;

import com.finance.portal.common.infrastructure.exception.ResourceNotFoundException;
import com.finance.portal.market.application.stock.MidasStockDetail;
import com.finance.portal.market.infrastructure.external.midas.MidasStockClient;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class StockQueryService {

    private static final Logger logger = LoggerFactory.getLogger(StockQueryService.class);

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

    private final YahooStockPort yahooStockPort;
    private final StockSymbolProvider stockSymbolProvider;
    private final MidasStockClient midasStockClient;

    public StockQueryService(YahooStockPort yahooStockPort, StockSymbolProvider stockSymbolProvider,
                             MidasStockClient midasStockClient) {
        this.yahooStockPort = yahooStockPort;
        this.stockSymbolProvider = stockSymbolProvider;
        this.midasStockClient = midasStockClient;
    }

    public StockSummary getStockSummary(String symbol) {
        YahooChartResponseDto.Meta meta = fetchMetaOrThrow(symbol);
        return mapToStockSummary(meta);
    }

    @Cacheable(
            cacheNames = "market.stocks.detail",
            key = "'detail:' + #symbol"
    )
    public StockDetail getStockDetail(String symbol) {
        YahooChartResponseDto.Meta meta = fetchMetaOrThrow(symbol);

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

    @Cacheable(
            cacheNames = "market.stocks.page",
            key = "'page:' + #page + ':size:' + #size"
    )
    public StockPageResponse getPagedStockSummaries(int page, int size) {
        return fetchPage(stockSymbolProvider.getPagedSymbols(page, size),
                stockSymbolProvider.getTotalElements(), page, size);
    }

    @Cacheable(
            cacheNames = "market.stocks.page",
            key = "'index:' + #index + ':page:' + #page + ':size:' + #size"
    )
    public StockPageResponse getPagedStockSummariesByIndex(int page, int size, String index) {
        List<String> allSymbols = switch (index) {
            case "BIST30"  -> stockSymbolProvider.getBist30Symbols();
            case "BIST50"  -> stockSymbolProvider.getBist50Symbols();
            case "BIST100" -> stockSymbolProvider.getBist100Symbols();
            default        -> stockSymbolProvider.getAllSymbols();
        };
        int total = allSymbols.size();
        int start = page * size;
        List<String> paged = start >= total ? List.of()
                : allSymbols.subList(start, Math.min(start + size, total));
        return fetchPage(paged, total, page, size);
    }

    private StockPageResponse fetchPage(List<String> symbols, int totalElements, int page, int size) {
        List<StockSummary> content = new java.util.ArrayList<>();
        if (!symbols.isEmpty()) {
            java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(5);
            try {
                List<java.util.concurrent.Future<StockSummary>> futures = symbols.stream()
                    .map(symbol -> executor.submit(() -> {
                        try { return getStockSummary(symbol); }
                        catch (Exception ex) {
                            logger.warn("Failed to fetch stock summary for {}: {}", symbol, ex.getMessage());
                            return null;
                        }
                    }))
                    .toList();
                for (java.util.concurrent.Future<StockSummary> f : futures) {
                    try {
                        StockSummary s = f.get(5, java.util.concurrent.TimeUnit.SECONDS);
                        if (s != null) content.add(s);
                    } catch (Exception ex) {
                        logger.warn("Stock summary future failed: {}", ex.getMessage());
                    }
                }
            } finally {
                executor.shutdown();
            }
        }
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        StockPageResponse response = new StockPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        return response;
    }

    public StockChartResponse getStockChart(String symbol) {        YahooChartResponseDto response = yahooStockPort.fetchChart(symbol);

        if (response == null
                || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {

            logger.info("Stock chart not found for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock not found for symbol: " + symbol);
        }

        YahooChartResponseDto.Result result = response.getChart().getResult().get(0);

        List<Long> timestamps = result.getTimestamp();
        List<java.math.BigDecimal> closes = null;

        if (result.getIndicators() != null
                && result.getIndicators().getQuote() != null
                && !result.getIndicators().getQuote().isEmpty()) {
            closes = result.getIndicators().getQuote().get(0).getClose();
        }

        if (timestamps == null || closes == null || timestamps.isEmpty() || closes.isEmpty()) {
            logger.info("Stock chart data missing for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock chart data not available for symbol: " + symbol);
        }

        int size = Math.min(timestamps.size(), closes.size());
        List<Long> ts = timestamps;
        List<BigDecimal> cl = closes;

        List<Long> filteredTimestamps = java.util.stream.IntStream.range(0, size)
                .filter(i -> ts.get(i) != null && cl.get(i) != null)
                .mapToObj(ts::get)
                .toList();

        List<BigDecimal> filteredCloses = java.util.stream.IntStream.range(0, size)
                .filter(i -> ts.get(i) != null && cl.get(i) != null)
                .mapToObj(cl::get)
                .toList();

        StockChartResponse chartResponse = new StockChartResponse();
        chartResponse.setSymbol(symbol);
        chartResponse.setTimestamps(filteredTimestamps);
        chartResponse.setClosePrices(filteredCloses);

        return chartResponse;
    }

    @Cacheable(cacheNames = "market.stocks.midas", key = "'midas:' + #symbol")
    public MidasStockDetail getMidasDetail(String symbol) {
        return midasStockClient.fetchDetail(symbol);
    }

    @Cacheable(
            cacheNames = "market.stocks.chart",
            key = "#symbol + ':' + #range + ':' + #interval"
    )
    public StockChartResponse getStockChartWithParams(String symbol, String range, String interval) {        YahooChartResponseDto response = yahooStockPort.fetchChartWithParams(symbol, range, interval);

        if (response == null || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {
            throw new ResourceNotFoundException("Stock chart not found for symbol: " + symbol);
        }

        YahooChartResponseDto.Result result = response.getChart().getResult().get(0);
        List<Long> timestamps = result.getTimestamp();
        List<BigDecimal> closes = null;
        if (result.getIndicators() != null && result.getIndicators().getQuote() != null
                && !result.getIndicators().getQuote().isEmpty()) {
            closes = result.getIndicators().getQuote().get(0).getClose();
        }
        if (timestamps == null || closes == null || timestamps.isEmpty()) {
            throw new ResourceNotFoundException("Stock chart data not available for symbol: " + symbol);
        }
        int sz = Math.min(timestamps.size(), closes.size());
        List<Long> ts = timestamps;
        List<BigDecimal> cl = closes;
        List<Long> filteredTs = java.util.stream.IntStream.range(0, sz)
                .filter(i -> ts.get(i) != null && cl.get(i) != null)
                .mapToObj(ts::get).toList();
        List<BigDecimal> filteredCl = java.util.stream.IntStream.range(0, sz)
                .filter(i -> ts.get(i) != null && cl.get(i) != null)
                .mapToObj(cl::get).toList();
        StockChartResponse chartResponse = new StockChartResponse();
        chartResponse.setSymbol(symbol);
        chartResponse.setTimestamps(filteredTs);
        chartResponse.setClosePrices(filteredCl);
        return chartResponse;
    }

    /**
     * OHLC (mum grafiği) verisi döndürür.
     * Her eleman: {time, open, high, low, close, volume}
     */
    public List<java.util.Map<String, Object>> getStockOhlc(String symbol, String range, String interval) {
        YahooChartResponseDto response = yahooStockPort.fetchChartWithParams(symbol, range, interval);
        if (response == null || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {
            throw new ResourceNotFoundException("OHLC data not found for symbol: " + symbol);
        }
        YahooChartResponseDto.Result result = response.getChart().getResult().get(0);
        List<Long> timestamps = result.getTimestamp();
        if (timestamps == null || result.getIndicators() == null
                || result.getIndicators().getQuote() == null
                || result.getIndicators().getQuote().isEmpty()) {
            return List.of();
        }
        YahooChartResponseDto.Quote q = result.getIndicators().getQuote().get(0);
        List<java.util.Map<String, Object>> data = new java.util.ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal o = safeGet(q.getOpen(), i);
            BigDecimal h = safeGet(q.getHigh(), i);
            BigDecimal l = safeGet(q.getLow(), i);
            BigDecimal c = safeGet(q.getClose(), i);
            if (o == null || h == null || l == null || c == null) continue;
            java.util.Map<String, Object> candle = new java.util.LinkedHashMap<>();
            candle.put("time", timestamps.get(i));
            candle.put("open",  o);
            candle.put("high",  h);
            candle.put("low",   l);
            candle.put("close", c);
            Long vol = (q.getVolume() != null && i < q.getVolume().size()) ? q.getVolume().get(i) : null;
            candle.put("volume", vol);
            data.add(candle);
        }
        return data;
    }

    private BigDecimal safeGet(List<BigDecimal> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private YahooChartResponseDto.Meta fetchMetaOrThrow(String symbol) {
        YahooChartResponseDto response = yahooStockPort.fetchChart(symbol);

        if (response == null
                || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()
                || response.getChart().getResult().get(0).getMeta() == null) {

            logger.info("Stock not found for symbol: {}", symbol);
            throw new ResourceNotFoundException("Stock not found for symbol: " + symbol);
        }

        return response.getChart().getResult().get(0).getMeta();
    }

    private StockSummary mapToStockSummary(YahooChartResponseDto.Meta meta) {
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
                ISTANBUL_ZONE
        );
        return dateTime.toString();
    }
}

