package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.port.ViopChartDataPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class IsYatirimViopChartAdapter implements ViopChartDataPort {

    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IsYatirimViopChartClient chartClient;

    public IsYatirimViopChartAdapter(IsYatirimViopChartClient chartClient) {
        this.chartClient = chartClient;
    }

    @Override
    public List<ViopChartPoint> fetchChart(String endeksCode, LocalDateTime from, LocalDateTime to, int candleMinutes) {
        return chartClient.fetchChart(endeksCode, from, to, candleMinutes).stream()
                .map(this::toChartPoint)
                .toList();
    }

    private ViopChartPoint toChartPoint(IsYatirimChartPoint point) {
        String dateTime = Instant.ofEpochMilli(point.getTimestamp())
                .atZone(TURKEY_ZONE)
                .toLocalDateTime()
                .format(DT_FORMATTER);
        return new ViopChartPoint(point.getTimestamp(), dateTime, point.getValue());
    }
}
