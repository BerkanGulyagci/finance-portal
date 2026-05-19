package com.finance.portal.market.application.crypto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoFullHistorySupportTest {

    @Test
    void marketChartToDailyOhlc_aggregatesHourlyIntoDailyWicks() {
        long day1 = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli();
        long day2 = day1 + 86_400_000L;
        Map<String, Object> chart = Map.of(
                "prices", List.of(
                        List.of(day1, 100.0),
                        List.of(day1 + 3_600_000L, 110.0),
                        List.of(day1 + 7_200_000L, 95.0),
                        List.of(day2, 105.0),
                        List.of(day2 + 3_600_000L, 120.0)
                )
        );

        List<List<Number>> rows = CryptoFullHistorySupport.marketChartToDailyOhlc(chart);

        assertThat(rows).hasSize(2);

        List<Number> first = rows.get(0);
        assertThat(first.get(1).doubleValue()).isEqualTo(100.0);
        assertThat(first.get(2).doubleValue()).isEqualTo(110.0);
        assertThat(first.get(3).doubleValue()).isEqualTo(95.0);
        assertThat(first.get(4).doubleValue()).isEqualTo(95.0);

        List<Number> second = rows.get(1);
        assertThat(second.get(1).doubleValue()).isEqualTo(105.0);
        assertThat(second.get(2).doubleValue()).isEqualTo(120.0);
        assertThat(second.get(3).doubleValue()).isEqualTo(105.0);
        assertThat(second.get(4).doubleValue()).isEqualTo(120.0);
    }
}
