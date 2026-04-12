package com.finance.portal.market.crypto.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinGeckoMarketItemDto {

    private String id;
    private String symbol;
    private String name;
    private String image;
    private Double currentPrice;
    private Double marketCap;
    private Integer marketCapRank;
    private Double totalVolume;
    @JsonProperty("high_24h")
    private Double high24h;
    @JsonProperty("low_24h")
    private Double low24h;
    @JsonProperty("price_change_24h")
    private Double priceChange24h;
    @JsonProperty("price_change_percentage_24h")
    private Double priceChangePercentage24h;
    @JsonProperty("price_change_percentage_1h_in_currency")
    private Double priceChangePercentage1hInCurrency;
    @JsonProperty("price_change_percentage_24h_in_currency")
    private Double priceChangePercentage24hInCurrency;
    @JsonProperty("price_change_percentage_7d_in_currency")
    private Double priceChangePercentage7dInCurrency;
    private String lastUpdated;
}
