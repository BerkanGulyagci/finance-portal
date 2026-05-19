package com.finance.portal.market.application.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.crypto.port.CoinGeckoPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CryptoMarketService {

    private static final Logger log = LoggerFactory.getLogger(CryptoMarketService.class);
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 250;
    private static final String CACHE_NAME = "cryptoMarketsCache";

    private static final int SYMBOL_SEARCH_PAGE_SIZE = 100;
    private static final int SYMBOL_SEARCH_MAX_PAGES = 5;

    private final CoinGeckoPort coinGeckoPort;
    private final CacheManager cacheManager;

    public CryptoMarketService(CoinGeckoPort coinGeckoPort, CacheManager cacheManager) {
        this.coinGeckoPort = coinGeckoPort;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "'try:p' + #page + ':s' + #size")
    @CircuitBreaker(name = "coinGeckoApi", fallbackMethod = "getCryptosFallback")
    @Retry(name = "coinGeckoApi")
    public List<CryptoMarketItem> getCryptos(int page, int size) {
        return getCryptos(page, size, "try");
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#currency + ':p' + #page + ':s' + #size")
    @CircuitBreaker(name = "coinGeckoApi", fallbackMethod = "getCryptosByCurrencyFallback")
    @Retry(name = "coinGeckoApi")
    public List<CryptoMarketItem> getCryptos(int page, int size, String currency) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }
        String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
        return coinGeckoPort.fetchMarkets(page + 1, size, cur);
    }

    public List<CryptoMarketItem> getAllCoins(String currency) {
        String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
        String cacheKey = cur + ":all";
        Cache cache = cacheManager.getCache(CACHE_NAME);

        if (cache != null) {
            Cache.ValueWrapper w = cache.get(cacheKey);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked")
                List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                log.debug("getAllCoins cache hit for currency={}", cur);
                return cached;
            }
        }

        List<CryptoMarketItem> result = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            try {
                List<CryptoMarketItem> pageItems = coinGeckoPort.fetchMarkets(page, 250, cur);
                if (pageItems.isEmpty()) break;
                result.addAll(pageItems);
                if (page < 4) Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("getAllCoins page={} currency={} failed: {}", page, cur, e.getMessage());
                if (!result.isEmpty()) break;
                throw new ExternalApiException("Crypto market data unavailable: " + e.getMessage(), e);
            }
        }

        if (cache != null && !result.isEmpty()) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    public CryptoMarketItem findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol must not be blank");
        String normalized = symbol.trim().toLowerCase();
        for (int page = 0; page < SYMBOL_SEARCH_MAX_PAGES; page++) {
            List<CryptoMarketItem> items = getCryptos(page, SYMBOL_SEARCH_PAGE_SIZE);
            Optional<CryptoMarketItem> match = items.stream()
                    .filter(item -> normalized.equals(item.getSymbol()))
                    .findFirst();
            if (match.isPresent()) return match.get();
            if (items.isEmpty()) break;
        }
        throw new ResourceNotFoundException(
                "Crypto not found in top " + (SYMBOL_SEARCH_MAX_PAGES * SYMBOL_SEARCH_PAGE_SIZE)
                        + " coins for symbol: " + symbol);
    }

    public Map<String, Object> getCoinDetail(String coinId) {
        return coinGeckoPort.fetchCoinDetail(coinId);
    }

    @Cacheable(cacheNames = "market.crypto.ohlc", key = "#coinId + ':v5:' + #days + ':' + #currency")
    @Retry(name = "coinGeckoApi")
    public List<List<Number>> getOhlc(String coinId, String days, String currency) {
        Object daysParam = resolveDaysParam(days);
        if ("max".equals(daysParam)) {
            return fetchFullHistoryOhlc(coinId, currency);
        }
        if (daysParam instanceof Integer d && (d == 90 || d == 180)) {
            Map<String, Object> hourlyChart = fetchHourlyMarketChart(coinId, currency, d);
            List<List<Number>> daily = CryptoFullHistorySupport.marketChartToDailyOhlc(hourlyChart);
            if (!daily.isEmpty()) {
                return daily;
            }
            log.warn("Daily OHLC from hourly chart empty for {}, falling back to CoinGecko /ohlc", coinId);
        }
        return coinGeckoPort.fetchOhlc(coinId, daysParam, currency);
    }

    @Cacheable(
            cacheNames = "market.crypto.chart",
            key = "#coinId + ':v4:' + #days + ':' + #currency + ':' + (#interval != null ? #interval : '') + ':' + (#aggregate != null ? #aggregate : '')"
    )
    @Retry(name = "coinGeckoApi")
    public Map<String, Object> getMarketChart(String coinId, String days, String currency,
                                              String interval, String aggregate) {
        Object daysParam = resolveDaysParam(days);
        String normalizedInterval = normalizeInterval(interval);

        Map<String, Object> chart;
        if ("max".equals(daysParam)) {
            chart = fetchFullHistoryChart(coinId, currency);
        } else if ("hourly".equals(normalizedInterval) && daysParam instanceof Integer d && d >= 90) {
            chart = fetchHourlyMarketChart(coinId, currency, d);
        } else {
            try {
                chart = coinGeckoPort.fetchMarketChart(coinId, daysParam, currency, normalizedInterval);
            } catch (ExternalApiException ex) {
                if ("hourly".equals(normalizedInterval) && daysParam instanceof Integer d && d >= 90) {
                    log.debug("CoinGecko hourly failed for {} days, trying chunked hourly: {}", d, ex.getMessage());
                    chart = fetchHourlyMarketChart(coinId, currency, d);
                } else {
                    throw ex;
                }
            }
        }

        if ("weekly".equalsIgnoreCase(aggregate)) {
            return CryptoChartAggregator.aggregate(chart, "weekly");
        }
        if ("monthly".equalsIgnoreCase(aggregate)) {
            return CryptoChartAggregator.aggregate(chart, "monthly");
        }
        return chart;
    }

    /**
     * CoinGecko saatlik veri: 90 gün tek istek; 180 gün iki 90 günlük range parçası (API ~100 gün hourly limiti).
     */
    private Map<String, Object> fetchHourlyMarketChart(String coinId, String currency, int days) {
        if (days <= 90) {
            return coinGeckoPort.fetchMarketChart(coinId, days, currency, "hourly");
        }

        long toSec = Instant.now().getEpochSecond();
        long fromSec = toSec - (long) days * 86_400L;
        long midSec = toSec - 90L * 86_400L;

        Map<String, Object> merged = Map.of();
        try {
            Map<String, Object> older = coinGeckoPort.fetchMarketChartRange(
                    coinId, currency, fromSec, midSec, "hourly");
            merged = CryptoFullHistorySupport.mergeCharts(merged, older);
        } catch (ExternalApiException ex) {
            log.warn("CoinGecko hourly range (older) failed for {}: {}", coinId, ex.getMessage());
        }

        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            Map<String, Object> recent = coinGeckoPort.fetchMarketChartRange(
                    coinId, currency, midSec, toSec, "hourly");
            merged = CryptoFullHistorySupport.mergeCharts(merged, recent);
        } catch (ExternalApiException ex) {
            log.warn("CoinGecko hourly range (recent) failed for {}: {}", coinId, ex.getMessage());
        }

        if (CryptoFullHistorySupport.hasChartData(merged)) {
            return merged;
        }
        log.warn("Hourly chart empty for {} days={}, falling back to daily auto granularity", coinId, days);
        return coinGeckoPort.fetchMarketChart(coinId, days, currency, null);
    }

    private Map<String, Object> fetchFullHistoryChart(String coinId, String currency) {
        Map<String, Object> fromMax = fetchMarketChartMaxWithRetry(coinId, currency);
        if (fromMax != null && CryptoFullHistorySupport.isAdequateChartHistory(fromMax)) {
            return fromMax;
        }
        if (fromMax != null) {
            log.info("CoinGecko days=max short history for {}, trying range", coinId);
        }

        Map<String, Object> ranged = fetchMarketChartRangeBestEffort(coinId, currency);
        if (CryptoFullHistorySupport.isAdequateChartHistory(ranged)) {
            return ranged;
        }
        if (CryptoFullHistorySupport.hasChartData(ranged)) {
            return ranged;
        }
        if (CryptoFullHistorySupport.hasChartData(fromMax)) {
            log.warn("Returning partial days=max chart for {} (range unavailable)", coinId);
            return fromMax;
        }
        throw new ExternalApiException("CoinGecko chart unavailable for " + coinId);
    }

    private Map<String, Object> fetchMarketChartRangeBestEffort(String coinId, String currency) {
        Map<String, Object> detail;
        try {
            detail = coinGeckoPort.fetchCoinDetail(coinId);
        } catch (ExternalApiException ex) {
            log.warn("Coin detail failed for {}: {}", coinId, ex.getMessage());
            return Map.of();
        }
        long fromSec = CryptoFullHistorySupport.resolveGenesisEpochSeconds(detail);
        long toSec = Instant.now().getEpochSecond();

        try {
            Map<String, Object> full = coinGeckoPort.fetchMarketChartRange(coinId, currency, fromSec, toSec);
            if (CryptoFullHistorySupport.hasChartData(full)) {
                return full;
            }
        } catch (ExternalApiException ex) {
            log.warn("CoinGecko full chart/range failed for {}: {}", coinId, ex.getMessage());
        }

        return fetchMarketChartRangeChunked(coinId, currency, fromSec, toSec);
    }

    private Map<String, Object> fetchMarketChartMaxWithRetry(String coinId, String currency) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return coinGeckoPort.fetchMarketChart(coinId, "max", currency, null);
            } catch (ExternalApiException ex) {
                log.warn("CoinGecko days=max attempt {}/3 for {}: {}", attempt, coinId, ex.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(1500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return null;
    }

    /** Range tek seferde patlarsa 180 günlük parçalar (rate-limit dostu). */
    private Map<String, Object> fetchMarketChartRangeChunked(String coinId, String currency, long fromSec, long toSec) {
        Map<String, Object> merged = Map.of();
        long chunkSec = 180L * 86_400L;
        long span = toSec - fromSec;
        long maxChunks = 24L;
        long startAt = fromSec;
        if (span > chunkSec * maxChunks) {
            startAt = toSec - chunkSec * maxChunks;
        }
        for (long start = startAt; start < toSec; start += chunkSec) {
            long end = Math.min(start + chunkSec, toSec);
            try {
                Map<String, Object> part = coinGeckoPort.fetchMarketChartRange(coinId, currency, start, end);
                merged = CryptoFullHistorySupport.mergeCharts(merged, part);
            } catch (ExternalApiException ex) {
                log.debug("chart/range chunk {}-{} failed: {}", start, end, ex.getMessage());
            }
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return merged;
    }

    private List<List<Number>> fetchFullHistoryOhlc(String coinId, String currency) {
        List<List<Number>> fromMax = null;
        try {
            fromMax = coinGeckoPort.fetchOhlc(coinId, "max", currency);
            if (CryptoFullHistorySupport.isAdequateOhlcHistory(fromMax)) {
                return fromMax;
            }
            log.info("CoinGecko OHLC max short history for {}, trying range", coinId);
        } catch (ExternalApiException ex) {
            log.warn("CoinGecko OHLC max failed for {}: {}", coinId, ex.getMessage());
        }

        List<List<Number>> ranged = fetchOhlcRangeBestEffort(coinId, currency);
        if (CryptoFullHistorySupport.isAdequateOhlcHistory(ranged)) {
            return ranged;
        }
        if (CryptoFullHistorySupport.hasOhlcData(ranged)) {
            return ranged;
        }
        if (CryptoFullHistorySupport.hasOhlcData(fromMax)) {
            log.warn("Returning partial OHLC max for {} (range unavailable)", coinId);
            return fromMax;
        }
        throw new ExternalApiException("CoinGecko OHLC unavailable for " + coinId);
    }

    private List<List<Number>> fetchOhlcRangeBestEffort(String coinId, String currency) {
        Map<String, Object> detail;
        try {
            detail = coinGeckoPort.fetchCoinDetail(coinId);
        } catch (ExternalApiException ex) {
            log.warn("Coin detail failed for {}: {}", coinId, ex.getMessage());
            return List.of();
        }
        long fromSec = CryptoFullHistorySupport.resolveGenesisEpochSeconds(detail);
        long toSec = Instant.now().getEpochSecond();

        try {
            List<List<Number>> full = coinGeckoPort.fetchOhlcRange(coinId, currency, fromSec, toSec);
            if (CryptoFullHistorySupport.hasOhlcData(full)) {
                return full;
            }
        } catch (ExternalApiException ex) {
            log.warn("CoinGecko full ohlc/range failed for {}: {}", coinId, ex.getMessage());
        }

        return fetchOhlcRangeChunked(coinId, currency, fromSec, toSec);
    }

    private List<List<Number>> fetchOhlcRangeChunked(String coinId, String currency, long fromSec, long toSec) {
        List<List<Number>> merged = List.of();
        long chunkSec = 180L * 86_400L;
        long span = toSec - fromSec;
        long maxChunks = 24L;
        long startAt = fromSec;
        if (span > chunkSec * maxChunks) {
            startAt = toSec - chunkSec * maxChunks;
        }
        for (long start = startAt; start < toSec; start += chunkSec) {
            long end = Math.min(start + chunkSec, toSec);
            try {
                List<List<Number>> part = coinGeckoPort.fetchOhlcRange(coinId, currency, start, end);
                merged = CryptoFullHistorySupport.mergeOhlc(merged, part);
            } catch (ExternalApiException ex) {
                log.debug("ohlc/range chunk {}-{} failed: {}", start, end, ex.getMessage());
            }
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return merged;
    }

    private static Object resolveDaysParam(String days) {
        if (days == null || days.isBlank()) {
            return 7;
        }
        if ("max".equalsIgnoreCase(days.trim())) {
            return "max";
        }
        try {
            int d = Integer.parseInt(days.trim());
            return Math.max(1, d);
        } catch (NumberFormatException ex) {
            return 7;
        }
    }

    private static String normalizeInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return null;
        }
        String v = interval.trim().toLowerCase();
        return ("hourly".equals(v) || "daily".equals(v)) ? v : null;
    }

    @SuppressWarnings("unused")
    public List<CryptoMarketItem> getCryptosFallback(int page, int size, Throwable t) {
        log.error("CoinGecko fallback page={} size={}: {}", page, size, t.getMessage());
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper w = cache.get("try:p" + page + ":s" + size);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked")
                List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                return cached;
            }
        }
        throw new ExternalApiException("Crypto market data is temporarily unavailable.", t);
    }

    @SuppressWarnings("unused")
    public List<CryptoMarketItem> getCryptosByCurrencyFallback(int page, int size, String currency, Throwable t) {
        log.error("CoinGecko fallback currency={} page={} size={}: {}", currency, page, size, t.getMessage());
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
            Cache.ValueWrapper w = cache.get(cur + ":p" + page + ":s" + size);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked")
                List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                return cached;
            }
        }
        throw new ExternalApiException("Crypto market data is temporarily unavailable.", t);
    }
}
