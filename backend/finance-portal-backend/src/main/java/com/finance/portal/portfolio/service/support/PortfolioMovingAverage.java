package com.finance.portal.portfolio.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fiyat serileri üzerinde basit hareketli ortalama.
 */
public final class PortfolioMovingAverage {

    private PortfolioMovingAverage() {
    }

    /**
     * Verilen kapanış fiyatı listesinin son {@code period} elemanının basit hareketli ortalamasını hesaplar.
     * Liste {@code period}'dan kısa ise {@code null} döner.
     */
    public static BigDecimal simpleMa(List<BigDecimal> closes, int period) {
        if (closes == null || closes.size() < period) {
            return null;
        }
        List<BigDecimal> window = closes.subList(closes.size() - period, closes.size());
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal c : window) {
            if (c != null) {
                sum = sum.add(c);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }
}
