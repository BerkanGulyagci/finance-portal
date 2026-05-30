package com.finance.portal.market.application.stock.port;

import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;

import java.util.List;

public interface YahooStockPort {

    YahooChartSnapshot fetchChart(String symbol);

    YahooChartSnapshot fetchChartWithParams(String symbol, String range, String interval);

    List<StockSummary> fetchQuoteBatch(List<String> symbols);

    /**
     * Verilen sorgu (genelde coin adı) için Yahoo'da CRYPTOCURRENCY tipli, {@code -USD} ile biten
     * sembolleri döndürür. Ticker çakışmalarında doğru Yahoo sembolünü (ör. {@code USDG33793-USD})
     * çözmek için kullanılır. Hata/boş → boş liste.
     */
    List<String> searchCryptoUsdSymbols(String query);
}
