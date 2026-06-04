package com.finance.portal.market.application.funds.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Rasyonet/YatırımDirekt fund-filter ve card endpoint'lerinden
 * normalize edilerek üretilen sade fon DTO'su.
 * TMF (TEFAS), TPF (BES), TAF (OKS) kategorileri için kullanılır.
 * Frontend'e bu DTO döndürülür.
 */
@Getter
@Setter
@NoArgsConstructor
public class RasyonetFundDto implements Serializable {

    /** Fon kategorisi: INVESTMENT_FUND | PENSION_FUND | AUTO_ENROLLMENT_FUND */
    private String fundCategory;

    private String uniqueCode;
    private String objectId;
    private String code;
    private String name;
    private Integer riskLevel;
    private BigDecimal price;
    private BigDecimal marketCapUsd;
    private String managerName;
    private String founderName;
    private String sourceCode;
    private String fundType;
    private Integer tradePlace;

    // Dönem getirileri
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

    // Detay alanları
    private String commission;
    private String benchmark;
    private String minimumQuantitySales;
    private String buySettlement;
    private String sellSettlement;
    private String strategy;
}
