package com.finance.portal.market.application.funds.model;

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

    public RasyonetOsmanliFundBulletinDto() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getUniqueCode()                        { return uniqueCode; }
    public void setUniqueCode(String v)                  { this.uniqueCode = v; }

    public String getCode()                              { return code; }
    public void setCode(String v)                        { this.code = v; }

    public String getName()                              { return name; }
    public void setName(String v)                        { this.name = v; }

    public String getGroup()                             { return group; }
    public void setGroup(String v)                       { this.group = v; }

    public String getType()                              { return type; }
    public void setType(String v)                        { this.type = v; }

    public Integer getRiskLevel()                        { return riskLevel; }
    public void setRiskLevel(Integer v)                  { this.riskLevel = v; }

    public String getCommission()                        { return commission; }
    public void setCommission(String v)                  { this.commission = v; }

    public String getBenchmark()                         { return benchmark; }
    public void setBenchmark(String v)                   { this.benchmark = v; }

    public String getFundCurrency()                      { return fundCurrency; }
    public void setFundCurrency(String v)                { this.fundCurrency = v; }

    public BigDecimal getMarketCap()                     { return marketCap; }
    public void setMarketCap(BigDecimal v)               { this.marketCap = v; }

    public BigDecimal getMarketCapUsd()                  { return marketCapUsd; }
    public void setMarketCapUsd(BigDecimal v)            { this.marketCapUsd = v; }

    public BigDecimal getDailyReturn()                   { return dailyReturn; }
    public void setDailyReturn(BigDecimal v)             { this.dailyReturn = v; }

    public BigDecimal getWeeklyReturn()                  { return weeklyReturn; }
    public void setWeeklyReturn(BigDecimal v)            { this.weeklyReturn = v; }

    public BigDecimal getMonthlyReturn()                 { return monthlyReturn; }
    public void setMonthlyReturn(BigDecimal v)           { this.monthlyReturn = v; }

    public BigDecimal getMonthlyReturnBenchmark()        { return monthlyReturnBenchmark; }
    public void setMonthlyReturnBenchmark(BigDecimal v)  { this.monthlyReturnBenchmark = v; }

    public String getMonthlyRelatedIndex()               { return monthlyRelatedIndex; }
    public void setMonthlyRelatedIndex(String v)         { this.monthlyRelatedIndex = v; }

    public BigDecimal getYearlyReturn()                  { return yearlyReturn; }
    public void setYearlyReturn(BigDecimal v)            { this.yearlyReturn = v; }

    public BigDecimal getYearlyReturnBenchmark()         { return yearlyReturnBenchmark; }
    public void setYearlyReturnBenchmark(BigDecimal v)   { this.yearlyReturnBenchmark = v; }

    public String getYearlyRelatedIndex()                { return yearlyRelatedIndex; }
    public void setYearlyRelatedIndex(String v)          { this.yearlyRelatedIndex = v; }
}
