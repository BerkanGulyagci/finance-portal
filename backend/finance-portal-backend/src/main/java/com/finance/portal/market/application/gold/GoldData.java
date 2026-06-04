package com.finance.portal.market.application.gold;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GoldData implements Serializable {

    // ONS fiyatı (USD)
    private BigDecimal priceUsd;
    // ONS fiyatı (TRY)
    private BigDecimal priceTry;
    // Gram altın (TRY)
    private BigDecimal gramTry;
    // Çeyrek altın (TRY)
    private BigDecimal ceyrekTry;
    // Yarım altın (TRY)
    private BigDecimal yarimTry;
    // Tam altın (TRY)
    private BigDecimal tamTry;

    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal previousClose;
    private BigDecimal change;
    private BigDecimal changePercent;
    private String currency;
    private String asOf;

    // Tarihsel kapanış fiyatları (USD) — grafik için
    private List<Long> timestamps;
    private List<BigDecimal> closePrices;
}
