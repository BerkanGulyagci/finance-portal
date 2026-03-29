package com.finance.portal.market.application.stock.port;

import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;

import java.util.List;

public interface YahooStockPort {

    YahooChartResponseDto fetchChart(String symbol);

    YahooChartResponseDto fetchChartWithParams(String symbol, String range, String interval);

    List<StockSummary> fetchQuoteBatch(List<String> symbols);
}

