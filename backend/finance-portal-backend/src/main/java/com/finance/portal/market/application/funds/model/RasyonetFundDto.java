package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Rasyonet/YatırımDirekt fund-filter ve card endpoint'lerinden
 * normalize edilerek üretilen sade fon DTO'su.
 * TMF (TEFAS), TPF (BES), TAF (OKS) kategorileri için kullanılır.
 * Frontend'e bu DTO döndürülür.
 */
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

    public RasyonetFundDto() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getFundCategory()            { return fundCategory; }
    public void setFundCategory(String v)      { this.fundCategory = v; }

    public String getUniqueCode()              { return uniqueCode; }
    public void setUniqueCode(String v)        { this.uniqueCode = v; }

    public String getObjectId()                { return objectId; }
    public void setObjectId(String v)          { this.objectId = v; }

    public String getCode()                    { return code; }
    public void setCode(String v)              { this.code = v; }

    public String getName()                    { return name; }
    public void setName(String v)              { this.name = v; }

    public Integer getRiskLevel()              { return riskLevel; }
    public void setRiskLevel(Integer v)        { this.riskLevel = v; }

    public BigDecimal getPrice()               { return price; }
    public void setPrice(BigDecimal v)         { this.price = v; }

    public BigDecimal getMarketCapUsd()        { return marketCapUsd; }
    public void setMarketCapUsd(BigDecimal v)  { this.marketCapUsd = v; }

    public String getManagerName()             { return managerName; }
    public void setManagerName(String v)       { this.managerName = v; }

    public String getFounderName()             { return founderName; }
    public void setFounderName(String v)       { this.founderName = v; }

    public String getSourceCode()              { return sourceCode; }
    public void setSourceCode(String v)        { this.sourceCode = v; }

    public String getFundType()                { return fundType; }
    public void setFundType(String v)          { this.fundType = v; }

    public Integer getTradePlace()             { return tradePlace; }
    public void setTradePlace(Integer v)       { this.tradePlace = v; }

    public BigDecimal getReturnOneDay()        { return returnOneDay; }
    public void setReturnOneDay(BigDecimal v)  { this.returnOneDay = v; }

    public BigDecimal getReturnOneWeek()       { return returnOneWeek; }
    public void setReturnOneWeek(BigDecimal v) { this.returnOneWeek = v; }

    public BigDecimal getReturnOneMonth()       { return returnOneMonth; }
    public void setReturnOneMonth(BigDecimal v) { this.returnOneMonth = v; }

    public BigDecimal getReturnThreeMonths()       { return returnThreeMonths; }
    public void setReturnThreeMonths(BigDecimal v) { this.returnThreeMonths = v; }

    public BigDecimal getReturnSixMonths()       { return returnSixMonths; }
    public void setReturnSixMonths(BigDecimal v) { this.returnSixMonths = v; }

    public BigDecimal getReturnYearToDate()       { return returnYearToDate; }
    public void setReturnYearToDate(BigDecimal v) { this.returnYearToDate = v; }

    public BigDecimal getReturnOneYear()       { return returnOneYear; }
    public void setReturnOneYear(BigDecimal v) { this.returnOneYear = v; }

    public BigDecimal getReturnTwoYears()       { return returnTwoYears; }
    public void setReturnTwoYears(BigDecimal v) { this.returnTwoYears = v; }

    public BigDecimal getReturnThreeYears()       { return returnThreeYears; }
    public void setReturnThreeYears(BigDecimal v) { this.returnThreeYears = v; }

    public BigDecimal getReturnFiveYears()       { return returnFiveYears; }
    public void setReturnFiveYears(BigDecimal v) { this.returnFiveYears = v; }

    public String getCommission()              { return commission; }
    public void setCommission(String v)        { this.commission = v; }

    public String getBenchmark()               { return benchmark; }
    public void setBenchmark(String v)         { this.benchmark = v; }

    public String getMinimumQuantitySales()    { return minimumQuantitySales; }
    public void setMinimumQuantitySales(String v) { this.minimumQuantitySales = v; }

    public String getBuySettlement()           { return buySettlement; }
    public void setBuySettlement(String v)     { this.buySettlement = v; }

    public String getSellSettlement()          { return sellSettlement; }
    public void setSellSettlement(String v)    { this.sellSettlement = v; }

    public String getStrategy()                { return strategy; }
    public void setStrategy(String v)          { this.strategy = v; }
}
