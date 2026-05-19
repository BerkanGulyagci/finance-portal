package com.finance.portal.market.application.stock.port;

import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;

import java.util.List;

public interface YahooStockPort {

    YahooChartSnapshot fetchChart(String symbol);

    YahooChartSnapshot fetchChartWithParams(String symbol, String range, String interval);

    List<StockSummary> fetchQuoteBatch(List<String> symbols);
}
