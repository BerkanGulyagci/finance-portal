package com.finance.portal.market.application.bond.eurobond.model;

import java.math.BigDecimal;

/** Business Insider Chart_GetChartData OHLC noktası. */
public record EurobondChartPoint(
        String date,
        BigDecimal close,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low
) {}
