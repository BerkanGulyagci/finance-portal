package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YahooStockAdapter implements YahooStockPort {

    private final YahooChartClient yahooChartClient;

    public YahooStockAdapter(YahooChartClient yahooChartClient) {
        this.yahooChartClient = yahooChartClient;
    }

    @Override
    public YahooChartSnapshot fetchChart(String symbol) {
        return YahooChartSnapshotMapper.fromDto(yahooChartClient.fetchChart(symbol));
    }

    @Override
    public YahooChartSnapshot fetchChartWithParams(String symbol, String range, String interval) {
        return YahooChartSnapshotMapper.fromDto(
                yahooChartClient.fetchChartWithParams(symbol, range, interval));
    }

    @Override
    public List<StockSummary> fetchQuoteBatch(List<String> symbols) {
        return yahooChartClient.fetchQuoteBatch(symbols);
    }

    @Override
    public List<String> searchCryptoUsdSymbols(String query) {
        return yahooChartClient.searchCryptoUsdSymbols(query);
    }
}
