package com.finance.portal.market.application.stock.port;

import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;

public interface YahooStockPort {

    YahooChartResponseDto fetchChart(String symbol);

    YahooChartResponseDto fetchChartWithParams(String symbol, String range, String interval);
}

