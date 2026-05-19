package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.port.BinanceKlinePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class BinanceKlineAdapter implements BinanceKlinePort {

    private final BinanceKlineClient binanceKlineClient;

    public BinanceKlineAdapter(BinanceKlineClient binanceKlineClient) {
        this.binanceKlineClient = binanceKlineClient;
    }

    @Override
    public List<CryptoChartCandle> fetchKlinesPage(String binanceSymbol, String interval, int limit, long startTimeMs) {
        List<List<Object>> rows = binanceKlineClient.fetchKlinesRaw(binanceSymbol, interval, limit, startTimeMs);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<CryptoChartCandle> candles = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            CryptoChartCandle candle = mapRow(row);
            if (candle != null) {
                candles.add(candle);
            }
        }
        return candles;
    }

    private static CryptoChartCandle mapRow(List<Object> row) {
        if (row == null || row.size() < 7) {
            return null;
        }
        Long openTimeMs = toLong(row.get(0));
        BigDecimal open = toBigDecimal(row.get(1));
        BigDecimal high = toBigDecimal(row.get(2));
        BigDecimal low = toBigDecimal(row.get(3));
        BigDecimal close = toBigDecimal(row.get(4));
        BigDecimal volume = toBigDecimal(row.get(5));
        Long closeTimeMs = toLong(row.get(6));
        if (openTimeMs == null || closeTimeMs == null || open == null || high == null || low == null || close == null) {
            return null;
        }
        if (close.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        long timestampSec = openTimeMs / 1000L;
        return new CryptoChartCandle(
                timestampSec,
                open,
                high,
                low,
                close,
                volume != null ? volume : BigDecimal.ZERO,
                closeTimeMs
        );
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
