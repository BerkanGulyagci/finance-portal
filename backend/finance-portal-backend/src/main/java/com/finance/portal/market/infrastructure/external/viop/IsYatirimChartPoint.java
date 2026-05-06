package com.finance.portal.market.infrastructure.external.viop;

import java.math.BigDecimal;

/**
 * İş Yatırım IndexHistoricalAll endpoint'inden gelen tek bir veri noktası.
 * Raw response: [[timestamp_ms, value], ...]
 */
public class IsYatirimChartPoint {

    private final Long timestamp;
    private final BigDecimal value;

    public IsYatirimChartPoint(Long timestamp, BigDecimal value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "IsYatirimChartPoint{timestamp=" + timestamp + ", value=" + value + '}';
    }
}
