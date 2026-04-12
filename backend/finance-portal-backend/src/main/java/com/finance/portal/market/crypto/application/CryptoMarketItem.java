package com.finance.portal.market.crypto.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Application/presentation contract for crypto market item (TRY).
 * Numeric fields are BigDecimal; JSON serialization uses plain numbers (no locale formatting).
 */
@Getter
public class CryptoMarketItem {

    private final String id;
    private final String symbol;
    private final String name;
    private final String image;
    private final BigDecimal currentPrice;
    private final BigDecimal marketCap;
    private final Integer marketCapRank;
    private final BigDecimal totalVolume;
    private final BigDecimal high24h;
    private final BigDecimal low24h;
    private final BigDecimal priceChange24h;
    private final BigDecimal priceChangePercentage24h;
    private final BigDecimal priceChangePercentage1h;
    private final BigDecimal priceChangePercentage7d;
    /** ISO-8601 timestamp from provider (e.g. 2026-03-08T23:08:21.063Z). */
    private final String lastUpdated;

    @JsonCreator
    public CryptoMarketItem(
            @JsonProperty("id") String id,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("name") String name,
            @JsonProperty("image") String image,
            @JsonProperty("currentPrice") BigDecimal currentPrice,
            @JsonProperty("marketCap") BigDecimal marketCap,
            @JsonProperty("marketCapRank") Integer marketCapRank,
            @JsonProperty("totalVolume") BigDecimal totalVolume,
            @JsonProperty("high24h") BigDecimal high24h,
            @JsonProperty("low24h") BigDecimal low24h,
            @JsonProperty("priceChange24h") BigDecimal priceChange24h,
            @JsonProperty("priceChangePercentage24h") BigDecimal priceChangePercentage24h,
            @JsonProperty("priceChangePercentage1h") BigDecimal priceChangePercentage1h,
            @JsonProperty("priceChangePercentage7d") BigDecimal priceChangePercentage7d,
            @JsonProperty("lastUpdated") String lastUpdated
    ) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.image = image;
        this.currentPrice = currentPrice;
        this.marketCap = marketCap;
        this.marketCapRank = marketCapRank;
        this.totalVolume = totalVolume;
        this.high24h = high24h;
        this.low24h = low24h;
        this.priceChange24h = priceChange24h;
        this.priceChangePercentage24h = priceChangePercentage24h;
        this.priceChangePercentage1h = priceChangePercentage1h;
        this.priceChangePercentage7d = priceChangePercentage7d;
        this.lastUpdated = lastUpdated;
    }
}
