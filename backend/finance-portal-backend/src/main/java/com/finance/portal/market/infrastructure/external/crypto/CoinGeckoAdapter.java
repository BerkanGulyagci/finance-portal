package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.crypto.port.CoinGeckoPort;
import com.finance.portal.market.infrastructure.external.crypto.dto.CoinGeckoMarketItemDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CoinGeckoAdapter implements CoinGeckoPort {

    private final CoinGeckoClient coinGeckoClient;

    public CoinGeckoAdapter(CoinGeckoClient coinGeckoClient) {
        this.coinGeckoClient = coinGeckoClient;
    }

    @Override
    public List<CryptoMarketItem> fetchMarkets(int coingeckoPage, int perPage, String currency) {
        return coinGeckoClient.fetchMarkets(coingeckoPage, perPage, currency).stream()
                .map(this::toApplicationModel)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> fetchCoinDetail(String coinId) {
        return coinGeckoClient.fetchCoinDetail(coinId);
    }

    @Override
    public List<List<Number>> fetchOhlc(String coinId, Object days, String currency) {
        return coinGeckoClient.fetchOhlc(coinId, days, currency);
    }

    @Override
    public List<List<Number>> fetchOhlcRange(String coinId, String currency, long fromEpochSec, long toEpochSec) {
        return coinGeckoClient.fetchOhlcRange(coinId, currency, fromEpochSec, toEpochSec);
    }

    @Override
    public Map<String, Object> fetchMarketChart(String coinId, Object days, String currency, String interval) {
        return coinGeckoClient.fetchMarketChart(coinId, days, currency, interval);
    }

    @Override
    public Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec) {
        return coinGeckoClient.fetchMarketChartRange(coinId, currency, fromEpochSec, toEpochSec);
    }

    @Override
    public Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec,
                                                     String interval) {
        return coinGeckoClient.fetchMarketChartRange(coinId, currency, fromEpochSec, toEpochSec, interval);
    }

    private CryptoMarketItem toApplicationModel(CoinGeckoMarketItemDto dto) {
        return new CryptoMarketItem(
                dto.getId(), dto.getSymbol(), dto.getName(), dto.getImage(),
                toBigDecimal(dto.getCurrentPrice()), toBigDecimal(dto.getMarketCap()),
                dto.getMarketCapRank(), toBigDecimal(dto.getTotalVolume()),
                toBigDecimal(dto.getHigh24h()), toBigDecimal(dto.getLow24h()),
                toBigDecimal(dto.getPriceChange24h()), toBigDecimal(dto.getPriceChangePercentage24h()),
                toBigDecimal(dto.getPriceChangePercentage1hInCurrency()),
                toBigDecimal(dto.getPriceChangePercentage7dInCurrency()),
                dto.getLastUpdated()
        );
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return BigDecimal.valueOf(value.doubleValue());
    }
}
