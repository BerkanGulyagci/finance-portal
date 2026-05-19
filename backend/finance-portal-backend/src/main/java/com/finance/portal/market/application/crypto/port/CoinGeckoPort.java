package com.finance.portal.market.application.crypto.port;

import com.finance.portal.market.application.crypto.model.CryptoMarketItem;

import java.util.List;
import java.util.Map;

/**
 * CoinGecko API adaptör portu.
 */
public interface CoinGeckoPort {

    List<CryptoMarketItem> fetchMarkets(int coingeckoPage, int perPage, String currency);

    Map<String, Object> fetchCoinDetail(String coinId);

    List<List<Number>> fetchOhlc(String coinId, Object days, String currency);

    List<List<Number>> fetchOhlcRange(String coinId, String currency, long fromEpochSec, long toEpochSec);

    Map<String, Object> fetchMarketChart(String coinId, Object days, String currency, String interval);

    Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec);

    Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec,
                                              String interval);
}
