package com.finance.portal.market.application.movers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Bir piyasa kategorisinin (kripto/hisse/döviz/emtia) günün en çok yükselen ve düşen enstrümanları.
 *
 * <p>NOT: Redis cache round-trip'i için {@code record} DEĞİL, {@code @JsonCreator}'lı sınıf
 * (bkz. {@link MarketMover}).
 */
@Getter
public class MoversCategory {

    private final String key;
    private final String label;
    private final List<MarketMover> gainers;
    private final List<MarketMover> losers;

    @JsonCreator
    public MoversCategory(
            @JsonProperty("key") String key,
            @JsonProperty("label") String label,
            @JsonProperty("gainers") List<MarketMover> gainers,
            @JsonProperty("losers") List<MarketMover> losers
    ) {
        this.key = key;
        this.label = label;
        this.gainers = gainers;
        this.losers = losers;
    }
}
