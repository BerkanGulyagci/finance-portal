package com.finance.portal.common.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${cache.news.ttl-seconds:300}")
    private long newsTtlSeconds;

    @Value("${cache.market.fx.tcmb.ttl-seconds:21600}")
    private long marketFxTcmbTtlSeconds;

    @Value("${cache.market.fx.open.ttl-seconds:1800}")
    private long marketFxOpenTtlSeconds;

    @Value("${cache.market.stocks.ttl-seconds:30}")
    private long marketStocksTtlSeconds;

    @Value("${cache.market.crypto.ttl-seconds:45}")
    private long marketCryptoTtlSeconds;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration newsCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(newsTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketFxTcmbCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketFxTcmbTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketFxOpenCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketFxOpenTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketStocksCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketStocksTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration cryptoMarketsCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketCryptoTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .withCacheConfiguration("newsCache", newsCacheConfig)
                .withCacheConfiguration("market.fx.tcmb.latest", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.fx.open.latest", marketFxOpenCacheConfig)
                .withCacheConfiguration("market.stocks.page", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.detail", marketStocksCacheConfig)
                .withCacheConfiguration("market.funds.detail", marketStocksCacheConfig)
                .withCacheConfiguration("market.funds.chart", marketStocksCacheConfig)
                .withCacheConfiguration("market.futures.page", marketStocksCacheConfig)
                .withCacheConfiguration("cryptoMarketsCache", cryptoMarketsCacheConfig)
                .build();
    }
}
