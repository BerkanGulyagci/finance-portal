package com.finance.portal.market.infrastructure.external.viop;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * İş Yatırım IndexHistoricalAll endpoint'inden gelen tek bir veri noktası.
 * Raw response: [[timestamp_ms, value], ...]
 */
@Getter
@AllArgsConstructor
public class IsYatirimChartPoint {

    private final Long timestamp;
    private final BigDecimal value;

    @Override
    public String toString() {
        return "IsYatirimChartPoint{timestamp=" + timestamp + ", value=" + value + '}';
    }
}
