package com.finance.portal.market.application.viop.port;

import com.finance.portal.market.application.viop.model.ViopChartPoint;

import java.time.LocalDateTime;
import java.util.List;

public interface ViopChartDataPort {

    List<ViopChartPoint> fetchChart(String endeksCode, LocalDateTime from, LocalDateTime to, int candleMinutes);
}
