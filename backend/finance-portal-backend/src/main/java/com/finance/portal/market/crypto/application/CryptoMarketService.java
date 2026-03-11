package com.finance.portal.market.crypto.application;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.market.crypto.infrastructure.CoinGeckoClient;
import com.finance.portal.market.crypto.infrastructure.dto.CoinGeckoMarketItemDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CryptoMarketService {

    private static final Logger log = LoggerFactory.getLogger(CryptoMarketService.class);
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;
    private static final String CACHE_NAME = "cryptoMarketsCache";
    private static final String CACHE_KEY_PREFIX = "try:p";

    private final CoinGeckoClient coinGeckoClient;
    private final CacheManager cacheManager;

    public CryptoMarketService(CoinGeckoClient coinGeckoClient, CacheManager cacheManager) {
        this.coinGeckoClient = coinGeckoClient;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "'try:p' + #page + ':s' + #size")
    @CircuitBreaker(name = "coinGeckoApi", fallbackMethod = "getCryptosFallback")
    @Retry(name = "coinGeckoApi")
    public List<CryptoMarketItem> getCryptos(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }

        int coingeckoPage = page + 1;
        List<CoinGeckoMarketItemDto> dtos = coinGeckoClient.fetchMarkets(coingeckoPage, size);
        return dtos.stream()
                .map(this::mapToCryptoMarketItem)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public List<CryptoMarketItem> getCryptosFallback(int page, int size, Throwable t) {
        log.error("CoinGecko API fallback triggered for page={} size={}: {}", page, size, t.getMessage());
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            String key = CACHE_KEY_PREFIX + page + ":s" + size;
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null && wrapper.get() != null) {
                @SuppressWarnings("unchecked")
                List<CryptoMarketItem> cached = (List<CryptoMarketItem>) wrapper.get();
                log.info("Returning cached crypto data for page={} size={}", page, size);
                return cached;
            }
        }
        throw new ExternalApiException(
                "Crypto market data is temporarily unavailable. Please try again later.", t);
    }

    private CryptoMarketItem mapToCryptoMarketItem(CoinGeckoMarketItemDto dto) {
        return new CryptoMarketItem(
                dto.getId(),
                dto.getSymbol(),
                dto.getName(),
                dto.getImage(),
                toBigDecimal(dto.getCurrentPrice()),
                toBigDecimal(dto.getMarketCap()),
                dto.getMarketCapRank(),
                toBigDecimal(dto.getTotalVolume()),
                toBigDecimal(dto.getHigh24h()),
                toBigDecimal(dto.getLow24h()),
                toBigDecimal(dto.getPriceChange24h()),
                toBigDecimal(dto.getPriceChangePercentage24h()),
                dto.getLastUpdated()
        );
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return BigDecimal.valueOf(value.doubleValue());
    }
}
