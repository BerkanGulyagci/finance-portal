package com.finance.portal.market.application.funds.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Rasyonet card endpoint'inden gelen zengin fon detay DTO'su.
 */
@Getter
@Setter
public class RasyonetFundDetailDto implements Serializable {

    // ── Temel ────────────────────────────────────────────────────────────────
    private String code;
    private String name;
    private String uniqueCode;
    private String objectId;
    private String currencyCode;
    private BigDecimal price;
    private BigDecimal priceUsd;
    private BigDecimal marketCap;
    private BigDecimal marketCapUsd;
    private Integer riskLevel;
    private String managerName;
    private String founderName;
    private String fundType;
    private String benchmarkName;
    private String kapLink;

    // ── Getiriler ─────────────────────────────────────────────────────────────
    private BigDecimal returnOneDay;
    private BigDecimal returnOneWeek;
    private BigDecimal returnOneMonth;
    private BigDecimal returnThreeMonths;
    private BigDecimal returnSixMonths;
    private BigDecimal returnYearToDate;
    private BigDecimal returnOneYear;
    private BigDecimal returnTwoYears;
    private BigDecimal returnThreeYears;
    private BigDecimal returnFiveYears;

    // ── Fon bilgileri ─────────────────────────────────────────────────────────
    private String strategy;
    private String commission;
    private String managementFeeAnnual;
    private String minimumQuantitySales;
    private String buySettlement;
    private String sellSettlement;

    // ── Risk ──────────────────────────────────────────────────────────────────
    private String riskBest;
    private String riskWorst;
    private String riskPositiveRateOfReturn;

    // ── Fiyat geçmişi (son 1 yıl) ────────────────────────────────────────────
    private List<PricePoint> priceHistory;

    // ── Aylık getiri ─────────────────────────────────────────────────────────
    private List<MonthlyReturn> monthlyReturns;

    // ── Varlık dağılımı ───────────────────────────────────────────────────────
    private List<AssetAllocationItem> assetAllocation;

    // ── İç sınıflar ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricePoint implements Serializable {
        private String date;
        private BigDecimal price;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyReturn implements Serializable {
        private String year;
        private String month;
        private BigDecimal value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetAllocationItem implements Serializable {
        private String name;
        private BigDecimal percentage;
    }
}
