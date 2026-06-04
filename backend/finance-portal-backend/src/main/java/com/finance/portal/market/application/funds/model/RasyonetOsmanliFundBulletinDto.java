package com.finance.portal.market.application.funds.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Rasyonet Osmanlı Portföy fon bülteni DTO'su.
 * Endpoint: GET /service-yatirimdirekt-home/web-menu/osmanli-fund-bulletin
 *
 * Response: {"Data": [...], "Status": "Ok", "Error": null}
 * Her item: UniqueCode, Code, Name, Group, Type, RiskLevel, Cosmission (typo),
 *           Kistas, FundCurrency, Mcap, McapUSD, DailyReturn, WeeklyReturn,
 *           MonthlyReturn, MonthlyReturnBenchmark, MonthlyRelatedIndex,
 *           YearlyReturn, YearlyReturnBenchmark, YearlyRelatedIndex
 */
@Getter
@Setter
@NoArgsConstructor
public class RasyonetOsmanliFundBulletinDto implements Serializable {

    private String     uniqueCode;
    private String     code;
    private String     name;
    private String     group;
    private String     type;           // Yatirim | Bes | Oks | Serbest
    private Integer    riskLevel;
    private String     commission;     // Cosmission typo → commission
    private String     benchmark;      // Kistas → benchmark
    private String     fundCurrency;
    private BigDecimal marketCap;
    private BigDecimal marketCapUsd;
    private BigDecimal dailyReturn;
    private BigDecimal weeklyReturn;
    private BigDecimal monthlyReturn;
    private BigDecimal monthlyReturnBenchmark;
    private String     monthlyRelatedIndex;
    private BigDecimal yearlyReturn;
    private BigDecimal yearlyReturnBenchmark;
    private String     yearlyRelatedIndex;
}
