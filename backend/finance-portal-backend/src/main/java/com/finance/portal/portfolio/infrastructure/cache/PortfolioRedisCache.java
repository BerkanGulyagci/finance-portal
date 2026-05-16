package com.finance.portal.portfolio.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.portfolio.application.port.PortfolioCachePort;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portföy GET yanıtları için cache-aside (PostgreSQL kaynak; Redis sadece hızlandırma).
 * Anahtarlar kullanıcıya özeldir: {@code portfolio:list:{userId}}, vb.
 */
@Component
public class PortfolioRedisCache implements PortfolioCachePort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRedisCache.class);

    static final String LIST_PREFIX = "portfolio:list:";
    static final String DETAIL_PREFIX = "portfolio:detail:";
    static final String WATCHLIST_PREFIX = "portfolio:watchlist:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Duration listTtl;
    private final Duration detailTtl;
    private final Duration watchlistTtl;

    public PortfolioRedisCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${cache.portfolio.list-ttl-seconds:30}") long listTtlSeconds,
            @Value("${cache.portfolio.detail-ttl-seconds:30}") long detailTtlSeconds,
            @Value("${cache.portfolio.watchlist-ttl-seconds:60}") long watchlistTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.listTtl = Duration.ofSeconds(Math.max(1, listTtlSeconds));
        this.detailTtl = Duration.ofSeconds(Math.max(1, detailTtlSeconds));
        this.watchlistTtl = Duration.ofSeconds(Math.max(1, watchlistTtlSeconds));
    }

    public static String listKey(String userId) {
        return LIST_PREFIX + userId;
    }

    public static String detailKey(String userId, UUID portfolioId) {
        return DETAIL_PREFIX + userId + ":" + portfolioId;
    }

    public static String watchlistKey(String userId, UUID portfolioId) {
        return WATCHLIST_PREFIX + userId + ":" + portfolioId;
    }

    @Override
    public Optional<List<PortfolioResponse>> getPortfolioList(String userId) {
        return readJson(listKey(userId), new TypeReference<>() {});
    }

    @Override
    public void putPortfolioList(String userId, List<PortfolioResponse> value) {
        writeJson(listKey(userId), value, listTtl);
    }

    @Override
    public Optional<PortfolioResponse> getPortfolioDetail(String userId, UUID portfolioId) {
        return readJson(detailKey(userId, portfolioId), new TypeReference<>() {});
    }

    @Override
    public void putPortfolioDetail(String userId, UUID portfolioId, PortfolioResponse value) {
        writeJson(detailKey(userId, portfolioId), value, detailTtl);
    }

    @Override
    public Optional<List<WatchlistItemResponse>> getWatchlist(String userId, UUID portfolioId) {
        return readJson(watchlistKey(userId, portfolioId), new TypeReference<>() {});
    }

    @Override
    public void putWatchlist(String userId, UUID portfolioId, List<WatchlistItemResponse> value) {
        writeJson(watchlistKey(userId, portfolioId), value, watchlistTtl);
    }

    /** createPortfolio */
    @Override
    public void evictList(String userId) {
        deleteSafe(listKey(userId));
    }

    /** deletePortfolio; addWatchlistItem; deleteWatchlistItem */
    @Override
    public void evictListDetailAndWatchlist(String userId, UUID portfolioId) {
        evictList(userId);
        deleteSafe(detailKey(userId, portfolioId));
        deleteSafe(watchlistKey(userId, portfolioId));
    }

    /** addTransaction; deleteTransaction */
    @Override
    public void evictListAndDetail(String userId, UUID portfolioId) {
        evictList(userId);
        deleteSafe(detailKey(userId, portfolioId));
    }

    private void deleteSafe(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete failed for key {}: {}", key, e.getMessage());
        }
    }

    private <T> Optional<T> readJson(String key, TypeReference<T> type) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeJson(String key, Object value, Duration ttl) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed for key {}: {}", key, e.getMessage());
        }
    }
}
