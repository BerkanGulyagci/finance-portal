package com.finance.portal.market.application.crypto;

import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.port.BinanceKlinePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TRY bazlı 5Y ve Tüm (max) grafikleri için Binance Spot klines.
 * USD/EUR 5Y/Tüm Yahoo akışında kalır (frontend).
 */
@Service
public class CryptoBinanceChartService {

    private static final Logger log = LoggerFactory.getLogger(CryptoBinanceChartService.class);

    private static final int KLINE_LIMIT = 1000;
    private static final long FIVE_YEARS_MS = 5L * 365L * 86_400_000L;

    private final BinanceKlinePort binanceKlinePort;

    public CryptoBinanceChartService(BinanceKlinePort binanceKlinePort) {
        this.binanceKlinePort = binanceKlinePort;
    }

    /**
     * @param baseSymbol örn. btc, eth
     * @param range      5y | max (all/tüm eşlemesi)
     * @param currency   yalnızca try için Binance kullanılır
     */
    @Cacheable(
            cacheNames = "market.crypto.binance.candles",
            key = "#baseSymbol + ':' + #range + ':' + #currency",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<CryptoChartCandle> getChartCandles(String baseSymbol, String range, String currency) {
        if (!shouldUseBinance(currency, range)) {
            return List.of();
        }
        String binanceSymbol = toBinanceTrySymbol(baseSymbol);
        if (binanceSymbol == null) {
            log.warn("Binance TRY chart skipped: invalid base symbol '{}'", baseSymbol);
            return List.of();
        }

        String normalizedRange = normalizeRange(range);
        String interval = resolveInterval(normalizedRange);
        long startTimeMs = resolveStartTimeMs(normalizedRange);

        List<CryptoChartCandle> candles = fetchAllKlines(binanceSymbol, interval, startTimeMs);
        if (candles.isEmpty()) {
            log.warn("Binance TRY klines empty for symbol={} range={} (pair may not exist on Binance)",
                    binanceSymbol, normalizedRange);
        }
        return candles;
    }

    public static boolean shouldUseBinance(String currency, String range) {
        if (currency == null || range == null) {
            return false;
        }
        String cur = currency.trim().toLowerCase(Locale.ROOT);
        if (!"try".equals(cur)) {
            return false;
        }
        String r = normalizeRange(range);
        return "5y".equals(r) || "max".equals(r);
    }

    static String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "";
        }
        String r = range.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(r) || "tüm".equals(r) || "tum".equals(r)) {
            return "max";
        }
        return r;
    }

    static String toBinanceTrySymbol(String baseSymbol) {
        if (baseSymbol == null || baseSymbol.isBlank()) {
            return null;
        }
        String base = baseSymbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            return null;
        }
        return base + "TRY";
    }

    private static String resolveInterval(String range) {
        // 5y / max ikisi de günlük: price-at eski tarih sorgularında haftalık aralık
        // floorEntry'yi Pazartesi'ye yapıştırıp 3-6 gün kayma yaratıyordu. Binance kline
        // pagination 1000/page ile çalışır → BTCTRY max (~2350 gün) 3 sayfada gelir.
        return switch (range) {
            case "5y", "max" -> "1d";
            default -> null;
        };
    }

    private static long resolveStartTimeMs(String range) {
        return switch (range) {
            case "5y" -> Instant.now().toEpochMilli() - FIVE_YEARS_MS;
            case "max" -> 0L;
            default -> 0L;
        };
    }

    private List<CryptoChartCandle> fetchAllKlines(String binanceSymbol, String interval, long startTimeMs) {
        if (interval == null) {
            return List.of();
        }

        List<CryptoChartCandle> all = new ArrayList<>();
        long cursor = startTimeMs;

        while (true) {
            List<CryptoChartCandle> batch = binanceKlinePort.fetchKlinesPage(
                    binanceSymbol, interval, KLINE_LIMIT, cursor);

            if (batch.isEmpty()) {
                break;
            }

            all.addAll(batch);

            CryptoChartCandle last = batch.get(batch.size() - 1);
            long nextStart = last.getCloseTimeMs() + 1;

            if (batch.size() < KLINE_LIMIT) {
                break;
            }
            if (nextStart <= cursor) {
                log.warn("Binance klines pagination stalled for {} at startTime={}", binanceSymbol, cursor);
                break;
            }
            cursor = nextStart;
        }

        return all;
    }
}
