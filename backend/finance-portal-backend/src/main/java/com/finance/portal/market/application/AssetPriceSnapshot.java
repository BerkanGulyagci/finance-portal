package com.finance.portal.market.application;

import com.finance.portal.common.domain.AssetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AssetPriceSnapshot {

    private final AssetType assetType;
    private final String symbol;
    private final BigDecimal price;
    private final String currency;
    private final LocalDateTime asOf;

    public AssetPriceSnapshot(AssetType assetType, String symbol, BigDecimal price,
                               String currency, LocalDateTime asOf) {
        this.assetType = assetType;
        this.symbol = symbol;
        this.price = price;
        this.currency = currency;
        this.asOf = asOf;
    }

    public AssetType getAssetType() { return assetType; }
    public String getSymbol()       { return symbol; }
    public BigDecimal getPrice()    { return price; }
    public String getCurrency()     { return currency; }
    public LocalDateTime getAsOf()  { return asOf; }
}
