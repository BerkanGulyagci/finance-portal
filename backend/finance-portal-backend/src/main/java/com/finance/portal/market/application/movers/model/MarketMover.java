package com.finance.portal.market.application.movers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Piyasanın hareketlileri (movers) kartı için tek enstrüman satırı.
 *
 * <p>NOT: Redis cache (GenericJackson2JsonRedisSerializer) round-trip'i için {@code record} DEĞİL,
 * {@code @JsonCreator}'lı klasik sınıf (bkz. CryptoMarketItem) — record'lar nested cache okumasında
 * "expected VALUE_STRING ... type id" hatası veriyordu.
 */
@Getter
public class MarketMover {

    /** CRYPTO | STOCK | FX | COMMODITY */
    private final String type;
    /** Navigasyon kimliği — kriptoda coinId, hissede/dövizde/emtiada sembol/kod (route için). */
    private final String id;
    /** Görünen sembol (BTC, THYAO, USD, GC=F …). */
    private final String symbol;
    /** Ad. */
    private final String name;
    /** Güncel fiyat. */
    private final BigDecimal price;
    /** Para birimi (TRY / USD). */
    private final String currency;
    /** Günlük % değişim. */
    private final BigDecimal changePercent;
    /** Logo URL (kripto), yoksa null. */
    private final String image;
    /** Günlük işlem hacmi (hacim liderleri için; movers'ta null olabilir). Birimi currency'ye göre. */
    private final BigDecimal volume;

    /** Eski 8-parametreli kurucu — hareketliler (movers) yolunda hacim taşınmaz (volume=null). */
    public MarketMover(String type, String id, String symbol, String name,
                       BigDecimal price, String currency, BigDecimal changePercent, String image) {
        this(type, id, symbol, name, price, currency, changePercent, image, null);
    }

    @JsonCreator
    public MarketMover(
            @JsonProperty("type") String type,
            @JsonProperty("id") String id,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("name") String name,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("currency") String currency,
            @JsonProperty("changePercent") BigDecimal changePercent,
            @JsonProperty("image") String image,
            @JsonProperty("volume") BigDecimal volume
    ) {
        this.type = type;
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.changePercent = changePercent;
        this.image = image;
        this.volume = volume;
    }
}
