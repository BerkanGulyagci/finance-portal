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

    // Fon GEÇMİŞİ (grafik) — NAV günde bir kez yayınlanır, geçmiş değişmez.
    // Bu yüzden tüm geçmişi 10 dk'da bir yeniden çekmek gereksiz; uzun TTL yeterli (varsayılan 6 saat).
    @Value("${cache.market.funds.history-ttl-seconds:21600}")
    private long marketFundsHistoryTtlSeconds;

    @Value("${cache.market.futures.ttl-seconds:600}")
    private long marketFuturesTtlSeconds;

    @Value("${cache.market.commodity.spot.ttl-seconds:60}")
    private long marketCommoditySpotTtlSeconds;

    @Value("${cache.market.commodity.history.ttl-seconds:600}")
    private long marketCommodityHistoryTtlSeconds;

    @Value("${cache.market.evds.bonds.active-series-ttl-seconds:43200}")
    private long evdsBondsActiveSeriesTtlSeconds;

    // Tahvil listesi 537+ enstrüman için EVDS'ten ağır yüklenir; warm-up scheduler @CachePut ile
    // 2 saatte bir tazeler. TTL bundan uzun (6 saat) ki warm-up'lar arasında asla expire olup
    // kullanıcıyı soğuk yola düşürmesin.
    @Value("${cache.market.evds.bonds.list-ttl-seconds:21600}")
    private long evdsBondsListTtlSeconds;

    @Value("${cache.market.evds.bonds.detail-ttl-seconds:3600}")
    private long evdsBondsDetailTtlSeconds;

    @Value("${cache.market.evds.bonds.history-ttl-seconds:7200}")
    private long evdsBondsHistoryTtlSeconds;

    @Value("${cache.market.economy.ttl-seconds:21600}")
    private long marketEconomyTtlSeconds;

    @Value("${cache.market.calendar.ttl-seconds:3600}")
    private long marketCalendarTtlSeconds;

    @Value("${cache.market.eurobond.list-ttl-seconds:1800}")
    private long eurobondListTtlSeconds;

    @Value("${cache.market.eurobond.detail-ttl-seconds:1800}")
    private long eurobondDetailTtlSeconds;

    @Value("${cache.market.eurobond.chart-ttl-seconds:3600}")
    private long eurobondChartTtlSeconds;

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

        // Fon GEÇMİŞİ (immutable NAV) — uzun TTL (varsayılan 6 saat)
        RedisCacheConfiguration marketFundsHistoryCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketFundsHistoryTtlSeconds))
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

        RedisCacheConfiguration cryptoChartCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration jsonDefaultCacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                // Enable per-cache hit/miss statistics so Micrometer exposes
                // cache_gets_total{result="hit|miss"} for the Grafana Cache Hit Rate panel.
                .enableStatistics()
                .cacheDefaults(jsonDefaultCacheConfig)
                .withCacheConfiguration("newsCache", newsCacheConfig)
                .withCacheConfiguration("market.fx.tcmb.latest", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.fx.open.latest", marketFxOpenCacheConfig)
                .withCacheConfiguration("market.fx.history", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(marketFxHistoryTtlSeconds))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.stocks.page", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.detail", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.midas", marketStocksCacheConfig)
                .withCacheConfiguration("market.stocks.chart", marketStocksCacheConfig)
                // BIST endeks listesi (~42 endeks, Yahoo snapshot) — hisse listesiyle aynı intraday TTL.
                .withCacheConfiguration("market.indices.list", marketStocksCacheConfig)
                .withCacheConfiguration("market.tefas.funds", marketFundsCacheConfig)
                .withCacheConfiguration("market.ipo", marketFundsCacheConfig)
                // VİOP: ViopService gerçek cache isimleri "market.viop.contracts" + "market.viop.detail".
                // (Eski "market.viop" ve "market.viop.chart" kayıtları ÖLÜYDÜ — hiçbir @Cacheable kullanmıyordu;
                //  market.viop.chart ise artık ViopChartService'te bellek-içi LRU. Gerçek isimler kaydedilmezse
                //  jsonDefault'a düşüp Redis'te HİÇ expire olmuyorlardı.)
                .withCacheConfiguration("market.viop.contracts", marketFuturesCacheConfig)
                .withCacheConfiguration("market.viop.detail", marketFuturesCacheConfig)
                // VİOP sözleşme spec'leri (İş Yatırım VadeliIslemler: büyüklük + teminat). YAML'da
                // OLMAYAN yeni sözleşmeler için. Teminatlar gün-bazlı güncellenir → 24h TTL.
                .withCacheConfiguration("market.viop.specs",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(24))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.indicators", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.gold.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.gold.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.silver.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.silver.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.precious.spot", marketFxTcmbCacheConfig)
                .withCacheConfiguration("market.precious.history", marketFundsCacheConfig)
                .withCacheConfiguration("market.tefas.history", marketFundsHistoryCacheConfig)
                .withCacheConfiguration("market.fund.history", marketFundsHistoryCacheConfig)
                .withCacheConfiguration("market.funds.page", marketFundsCacheConfig)
                .withCacheConfiguration("market.funds.detail", marketFundsCacheConfig)
                .withCacheConfiguration("market.funds.chart", marketFundsHistoryCacheConfig)
                // Fon strateji çevirisi (EN) — metin fon başına sabit, uzun TTL (history ile aynı, 6sa)
                .withCacheConfiguration("market.fund.strategy.translated", marketFundsHistoryCacheConfig)
                .withCacheConfiguration("market.futures.page", marketFuturesCacheConfig)
                .withCacheConfiguration("market.commodity.spot", marketCommoditySpotCacheConfig)
                .withCacheConfiguration("market.commodity.history", marketCommodityHistoryCacheConfig)
                .withCacheConfiguration("cryptoMarketsCache", cryptoMarketsCacheConfig)
                // Piyasanın hareketlileri (movers) — cache'li listelerden türetilir; 120 sn intraday tazelik için yeterli
                .withCacheConfiguration("market.movers",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(120))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                // Hacim liderleri — movers ile aynı kaynak/tazelik (günlük işlem hacmine göre top-N)
                .withCacheConfiguration("market.volumeLeaders",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(120))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.crypto.chart", cryptoChartCacheConfig)
                .withCacheConfiguration("market.crypto.ohlc", cryptoChartCacheConfig)
                .withCacheConfiguration("market.crypto.binance.candles", cryptoChartCacheConfig)
                .withCacheConfiguration("market.crypto.yahoo.ohlc", cryptoChartCacheConfig)
                .withCacheConfiguration("market.crypto.yahoo.chart", cryptoChartCacheConfig)
                // BIST endeksleri (XU100/XU030/XU050) Yahoo'dan günlük seri — 60 dk önbellek
                // (endeksler intraday yavaş hareket eder, ticker kullanımına yeterli).
                .withCacheConfiguration("market.bistIndex.history",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(60))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
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
                .withCacheConfiguration("market.economy",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(marketEconomyTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.calendar",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(marketCalendarTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.eurobond.list",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(eurobondListTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.eurobond.detail",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(eurobondDetailTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .withCacheConfiguration("market.eurobond.chart",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofSeconds(eurobondChartTtlSeconds))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer())))
                .build();
    }
}
