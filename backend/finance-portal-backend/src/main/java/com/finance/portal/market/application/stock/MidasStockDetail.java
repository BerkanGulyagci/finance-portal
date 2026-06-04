package com.finance.portal.market.application.stock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MidasStockDetail implements Serializable {

    private String symbol;
    private String name;
    private String logoUrl;
    // Price
    private String currentPrice;
    private String openPrice;
    private String bid;
    private String ask;
    // Change
    private String dailyChange;
    private String dailyChangePercent;
    // Volume
    private String dailyVolume;
    private String volumeLot;
    // Range
    private String weeklyHigh;
    private String weeklyLow;
    private String monthlyHigh;
    private String monthlyLow;
    private String upperLimit;
    private String lowerLimit;
    // Metrics
    private String marketCap;
    private String capital;
    private String peRatio;
    private String pbRatio;
    private String freeFloat;
    private String foreignRatio;
    private String volatility;
    private String netProfit;
    // Company
    private String ceo;
    private String employeeCount;
    private String foundedDate;
    private String ipoDate;
    private String sector;
    private String address;
    private String country;
    private String description;
    // Ownership
    private List<ShareholderItem> shareholders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareholderItem implements Serializable {
        private String name;
        private String sharePercent;
    }
}
