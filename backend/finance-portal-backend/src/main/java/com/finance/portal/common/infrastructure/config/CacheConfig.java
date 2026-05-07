package com.finance.portal.common.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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

    @Value("${cache.market.fx.history.ttl-seconds:3600}")
    private long marketFxHistoryTtlSeconds;

    @Value("${cache.market.stocks.ttl-seconds:600}")
    private long marketStocksTtlSeconds;

    @Value("${cache.market.crypto.ttl-seconds:60}")
    private long marketCryptoTtlSeconds;

    @Value("${cache.market.funds.ttl-seconds:600}")
    private long marketFundsTtlSeconds;

    @Value("${cache.market.futures.ttl-seconds:600}")
    private long marketFuturesTtlSeconds;

    @Value("${cache.market.commodity.spot.ttl-seconds:60}")
    private long marketCommoditySpotTtlSeconds;

    @Value("${cache.market.commodity.history.ttl-seconds:600}")
    private long marketCommodityHistoryTtlSeconds;

    @Value("${cache.market.evds.bonds.active-series-ttl-seconds:43200}")
    private long evdsBondsActiveSeriesTtlSeconds;

    @Value("${cache.market.evds.bonds.list-ttl-seconds:3600}")
    private long evdsBondsListTtlSeconds;

    @Value("${cache.market.evds.bonds.detail-ttl-seconds:3600}")
    private long evdsBondsDetailTtlSeconds;

    @Value("${cache.market.evds.bonds.history-ttl-seconds:7200}")
    private long evdsBondsHistoryTtlSeconds;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * String key / String value RedisTemplate.
     * BankCurrencyService tarafından manuel JSON cache için kullanılır.
     * NOT: Spring Boot RedisAutoConfiguration zaten stringRedisTemplate bean'i sağlar.
     * Bu bean burada tanımlanmaz — Spring Boot'un otomatik bean'i kullanılır.
     */

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

        RedisCacheConfiguration marketFundsCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketFundsTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketFuturesCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketFuturesTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketCommoditySpotCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketCommoditySpotTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        RedisCacheConfiguration marketCommodityHistoryCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketCommodityHistoryTtlSeconds))
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
                .withCacheConfiguration("market.fx.history", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(marketFxHistoryTtlSeconds))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.stocks.page", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.detail", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.midas", marketStocksCacheConfig)
                .withCacheConfiguration("market.tefas.funds", marketFundsCacheConfig)
                .withCacheConfiguration("market.ipo", marketFundsCacheConfig)
                .withCacheConfiguration("market.viop", marketFundsCacheConfig)
                .withCacheConfiguration("market.indicators", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.gold.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.gold.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.silver.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.silver.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.precious.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.precious.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.tefas.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.fund.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.funds.page", marketFundsCacheConfig)
                .withCacheConfiguration("market.funds.detail", marketFundsCacheConfig)
                .withCacheConfiguration("market.funds.chart", marketFundsCacheConfig)
                .withCacheConfiguration("market.futures.page", marketFuturesCacheConfig)
                .withCacheConfiguration("market.commodity.spot", marketCommoditySpotCacheConfig)
                .withCacheConfiguration("market.commodity.history", marketCommodityHistoryCacheConfig)
                .withCacheConfiguration("cryptoMarketsCache", cryptoMarketsCacheConfig)
                .withCacheConfiguration("market.evds.bonds.active-series",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(evdsBondsActiveSeriesTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.evds.bonds.list",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(evdsBondsListTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.evds.bonds.detail",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(evdsBondsDetailTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.evds.bonds.history",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(evdsBondsHistoryTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .build();
    }
}
