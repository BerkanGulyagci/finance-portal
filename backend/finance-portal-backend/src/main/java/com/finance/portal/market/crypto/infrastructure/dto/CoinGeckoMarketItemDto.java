package com.finance.portal.market.crypto.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    private Double high24h;
    private Double low24h;
    private Double priceChange24h;
    private Double priceChangePercentage24h;
    private String lastUpdated;
}
