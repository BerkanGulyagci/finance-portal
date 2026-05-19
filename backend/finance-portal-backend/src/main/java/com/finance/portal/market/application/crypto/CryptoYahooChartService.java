package com.finance.portal.market.application.crypto;

import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kripto 5Y / Tüm grafikleri — USD ve EUR için Yahoo Finance OHLC (BTC-USD, BTC-EUR).
 */
@Service
public class CryptoYahooChartService {

    private static final Logger log = LoggerFactory.getLogger(CryptoYahooChartService.class);

    private final YahooStockPort yahooStockPort;

    public CryptoYahooChartService(YahooStockPort yahooStockPort) {
        this.yahooStockPort = yahooStockPort;
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
        return switch (r) {
            case "5y" -> new String[]{"5y", "1wk"};
            case "max" -> new String[]{"max", "1mo"};
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
            String yahooSymbol = toYahooSymbol(baseSymbol, quote);
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
            String yahooSymbol = toYahooSymbol(baseSymbol, quote);
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
