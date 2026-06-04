package com.finance.portal.market.application;

import com.finance.portal.common.domain.AssetType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AssetPriceSnapshot {

    private final AssetType assetType;
    private final String symbol;
    private final BigDecimal price;
    private final String currency;
    private final LocalDateTime asOf;
}
