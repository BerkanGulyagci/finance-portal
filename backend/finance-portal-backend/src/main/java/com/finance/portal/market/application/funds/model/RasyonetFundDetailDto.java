package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Rasyonet card endpoint'inden gelen zengin fon detay DTO'su.
 */
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

    public static class PricePoint implements Serializable {
        private String date;
        private BigDecimal price;

        public PricePoint() {}
        public PricePoint(String date, BigDecimal price) { this.date = date; this.price = price; }

        public String getDate()          { return date; }
        public void setDate(String v)    { this.date = v; }
        public BigDecimal getPrice()     { return price; }
        public void setPrice(BigDecimal v) { this.price = v; }
    }

    public static class MonthlyReturn implements Serializable {
        private String year;
        private String month;
        private BigDecimal value;

        public MonthlyReturn() {}
        public MonthlyReturn(String year, String month, BigDecimal value) {
            this.year = year; this.month = month; this.value = value;
        }

        public String getYear()           { return year; }
        public void setYear(String v)     { this.year = v; }
        public String getMonth()          { return month; }
        public void setMonth(String v)    { this.month = v; }
        public BigDecimal getValue()      { return value; }
        public void setValue(BigDecimal v){ this.value = v; }
    }

    public static class AssetAllocationItem implements Serializable {
        private String name;
        private BigDecimal percentage;

        public AssetAllocationItem() {}
        public AssetAllocationItem(String name, BigDecimal percentage) {
            this.name = name; this.percentage = percentage;
        }

        public String getName()               { return name; }
        public void setName(String v)         { this.name = v; }
        public BigDecimal getPercentage()     { return percentage; }
        public void setPercentage(BigDecimal v) { this.percentage = v; }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getCode()                    { return code; }
    public void setCode(String v)              { this.code = v; }
    public String getName()                    { return name; }
    public void setName(String v)              { this.name = v; }
    public String getUniqueCode()              { return uniqueCode; }
    public void setUniqueCode(String v)        { this.uniqueCode = v; }
    public String getObjectId()                { return objectId; }
    public void setObjectId(String v)          { this.objectId = v; }
    public String getCurrencyCode()            { return currencyCode; }
    public void setCurrencyCode(String v)      { this.currencyCode = v; }
    public BigDecimal getPrice()               { return price; }
    public void setPrice(BigDecimal v)         { this.price = v; }
    public BigDecimal getPriceUsd()            { return priceUsd; }
    public void setPriceUsd(BigDecimal v)      { this.priceUsd = v; }
    public BigDecimal getMarketCap()           { return marketCap; }
    public void setMarketCap(BigDecimal v)     { this.marketCap = v; }
    public BigDecimal getMarketCapUsd()        { return marketCapUsd; }
    public void setMarketCapUsd(BigDecimal v)  { this.marketCapUsd = v; }
    public Integer getRiskLevel()              { return riskLevel; }
    public void setRiskLevel(Integer v)        { this.riskLevel = v; }
    public String getManagerName()             { return managerName; }
    public void setManagerName(String v)       { this.managerName = v; }
    public String getFounderName()             { return founderName; }
    public void setFounderName(String v)       { this.founderName = v; }
    public String getFundType()                { return fundType; }
    public void setFundType(String v)          { this.fundType = v; }
    public String getBenchmarkName()           { return benchmarkName; }
    public void setBenchmarkName(String v)     { this.benchmarkName = v; }
    public String getKapLink()                 { return kapLink; }
    public void setKapLink(String v)           { this.kapLink = v; }

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

    public String getStrategy()                { return strategy; }
    public void setStrategy(String v)          { this.strategy = v; }
    public String getCommission()              { return commission; }
    public void setCommission(String v)        { this.commission = v; }
    public String getManagementFeeAnnual()     { return managementFeeAnnual; }
    public void setManagementFeeAnnual(String v) { this.managementFeeAnnual = v; }
    public String getMinimumQuantitySales()    { return minimumQuantitySales; }
    public void setMinimumQuantitySales(String v) { this.minimumQuantitySales = v; }
    public String getBuySettlement()           { return buySettlement; }
    public void setBuySettlement(String v)     { this.buySettlement = v; }
    public String getSellSettlement()          { return sellSettlement; }
    public void setSellSettlement(String v)    { this.sellSettlement = v; }

    public String getRiskBest()                { return riskBest; }
    public void setRiskBest(String v)          { this.riskBest = v; }
    public String getRiskWorst()               { return riskWorst; }
    public void setRiskWorst(String v)         { this.riskWorst = v; }
    public String getRiskPositiveRateOfReturn() { return riskPositiveRateOfReturn; }
    public void setRiskPositiveRateOfReturn(String v) { this.riskPositiveRateOfReturn = v; }

    public List<PricePoint> getPriceHistory()          { return priceHistory; }
    public void setPriceHistory(List<PricePoint> v)    { this.priceHistory = v; }
    public List<MonthlyReturn> getMonthlyReturns()     { return monthlyReturns; }
    public void setMonthlyReturns(List<MonthlyReturn> v) { this.monthlyReturns = v; }
    public List<AssetAllocationItem> getAssetAllocation()          { return assetAllocation; }
    public void setAssetAllocation(List<AssetAllocationItem> v)    { this.assetAllocation = v; }
}
