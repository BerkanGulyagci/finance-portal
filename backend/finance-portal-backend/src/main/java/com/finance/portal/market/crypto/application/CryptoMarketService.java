package com.finance.portal.market.crypto.application;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.common.infrastructure.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CryptoMarketService {

    private static final Logger log = LoggerFactory.getLogger(CryptoMarketService.class);
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 250;
    private static final String CACHE_NAME = "cryptoMarketsCache";

    private static final int SYMBOL_SEARCH_PAGE_SIZE = 100;
    private static final int SYMBOL_SEARCH_MAX_PAGES = 5;

    private final CoinGeckoClient coinGeckoClient;
    private final CacheManager cacheManager;

    public CryptoMarketService(CoinGeckoClient coinGeckoClient, CacheManager cacheManager) {
        this.coinGeckoClient = coinGeckoClient;
        this.cacheManager = cacheManager;
    }

    /** TRY bazlı liste (geriye dönük uyumluluk). */
    @Cacheable(cacheNames = CACHE_NAME, key = "'try:p' + #page + ':s' + #size")
    @CircuitBreaker(name = "coinGeckoApi", fallbackMethod = "getCryptosFallback")
    @Retry(name = "coinGeckoApi")
    public List<CryptoMarketItem> getCryptos(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < MIN_SIZE || size > MAX_SIZE)
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        List<CoinGeckoMarketItemDto> dtos = coinGeckoClient.fetchMarkets(page + 1, size, "try");
        return dtos.stream().map(this::mapToCryptoMarketItem).collect(Collectors.toList());
    }

    /** İstenen para birimi (try/usd/eur) bazlı liste — her currency için ayrı CoinGecko isteği. */
    @Cacheable(cacheNames = CACHE_NAME, key = "#currency + ':p' + #page + ':s' + #size")
    @CircuitBreaker(name = "coinGeckoApi", fallbackMethod = "getCryptosByCurrencyFallback")
    @Retry(name = "coinGeckoApi")
    public List<CryptoMarketItem> getCryptos(int page, int size, String currency) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < MIN_SIZE || size > MAX_SIZE)
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
        List<CoinGeckoMarketItemDto> dtos = coinGeckoClient.fetchMarkets(page + 1, size, cur);
        return dtos.stream().map(this::mapToCryptoMarketItem).collect(Collectors.toList());
    }

    /**
     * Market cap sıralamasına göre ilk 1000 coini çeker (4 × 250).
     * Sayfalar arası 1.2s bekleme ile rate-limit koruması sağlanır.
     * Bu metod doğrudan çağrılır — @Cacheable proxy sorunu olmadan manuel cache yönetimi.
     */
    public List<CryptoMarketItem> getAllCoins(String currency) {
        String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
        String cacheKey = cur + ":all";
        Cache cache = cacheManager.getCache(CACHE_NAME);

        // Cache'de varsa direkt dön
        if (cache != null) {
            Cache.ValueWrapper w = cache.get(cacheKey);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked")
                List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                log.debug("getAllCoins cache hit for currency={}", cur);
                return cached;
            }
        }

        List<CryptoMarketItem> result = new java.util.ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            try {
                List<CoinGeckoMarketItemDto> dtos = coinGeckoClient.fetchMarkets(page, 250, cur);
                if (dtos.isEmpty()) break;
                dtos.stream().map(this::mapToCryptoMarketItem).forEach(result::add);
                if (page < 4) Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("getAllCoins page={} currency={} failed: {}", page, cur, e.getMessage());
                // Kısmi sonuç varsa onu dön
                if (!result.isEmpty()) break;
                throw new ExternalApiException("Crypto market data unavailable: " + e.getMessage(), e);
            }
        }

        // Manuel cache'e yaz
        if (cache != null && !result.isEmpty()) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    public CryptoMarketItem findBySymbol(String symbol) {        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol must not be blank");
        String normalized = symbol.trim().toLowerCase();
        for (int page = 0; page < SYMBOL_SEARCH_MAX_PAGES; page++) {
            List<CryptoMarketItem> items = getCryptos(page, SYMBOL_SEARCH_PAGE_SIZE);
            Optional<CryptoMarketItem> match = items.stream()
                    .filter(item -> normalized.equals(item.getSymbol())).findFirst();
            if (match.isPresent()) return match.get();
            if (items.isEmpty()) break;
        }
        throw new ResourceNotFoundException(
                "Crypto not found in top " + (SYMBOL_SEARCH_MAX_PAGES * SYMBOL_SEARCH_PAGE_SIZE)
                + " coins for symbol: " + symbol);
    }

    @SuppressWarnings("unused")
    public List<CryptoMarketItem> getCryptosFallback(int page, int size, Throwable t) {
        log.error("CoinGecko fallback page={} size={}: {}", page, size, t.getMessage());
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper w = cache.get("try:p" + page + ":s" + size);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked") List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                return cached;
            }
        }
        throw new ExternalApiException("Crypto market data is temporarily unavailable.", t);
    }

    @SuppressWarnings("unused")
    public List<CryptoMarketItem> getCryptosByCurrencyFallback(int page, int size, String currency, Throwable t) {
        log.error("CoinGecko fallback currency={} page={} size={}: {}", currency, page, size, t.getMessage());
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            String cur = (currency == null || currency.isBlank()) ? "try" : currency.trim().toLowerCase();
            Cache.ValueWrapper w = cache.get(cur + ":p" + page + ":s" + size);
            if (w != null && w.get() != null) {
                @SuppressWarnings("unchecked") List<CryptoMarketItem> cached = (List<CryptoMarketItem>) w.get();
                return cached;
            }
        }
        throw new ExternalApiException("Crypto market data is temporarily unavailable.", t);
    }

    private CryptoMarketItem mapToCryptoMarketItem(CoinGeckoMarketItemDto dto) {
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
