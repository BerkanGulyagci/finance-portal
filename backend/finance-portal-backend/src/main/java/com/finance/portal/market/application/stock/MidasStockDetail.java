package com.finance.portal.market.application.stock;

import java.io.Serializable;
import java.util.List;

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

    public MidasStockDetail() {}

    public static class ShareholderItem implements Serializable {
        private String name;
        private String sharePercent;
        public ShareholderItem() {}
        public ShareholderItem(String name, String sharePercent) { this.name = name; this.sharePercent = sharePercent; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSharePercent() { return sharePercent; }
        public void setSharePercent(String sharePercent) { this.sharePercent = sharePercent; }
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String s) { this.symbol = s; }
    public String getName() { return name; }
    public void setName(String s) { this.name = s; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String s) { this.logoUrl = s; }
    public String getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(String s) { this.currentPrice = s; }
    public String getOpenPrice() { return openPrice; }
    public void setOpenPrice(String s) { this.openPrice = s; }
    public String getBid() { return bid; }
    public void setBid(String s) { this.bid = s; }
    public String getAsk() { return ask; }
    public void setAsk(String s) { this.ask = s; }
    public String getDailyChange() { return dailyChange; }
    public void setDailyChange(String s) { this.dailyChange = s; }
    public String getDailyChangePercent() { return dailyChangePercent; }
    public void setDailyChangePercent(String s) { this.dailyChangePercent = s; }
    public String getDailyVolume() { return dailyVolume; }
    public void setDailyVolume(String s) { this.dailyVolume = s; }
    public String getVolumeLot() { return volumeLot; }
    public void setVolumeLot(String s) { this.volumeLot = s; }
    public String getWeeklyHigh() { return weeklyHigh; }
    public void setWeeklyHigh(String s) { this.weeklyHigh = s; }
    public String getWeeklyLow() { return weeklyLow; }
    public void setWeeklyLow(String s) { this.weeklyLow = s; }
    public String getMonthlyHigh() { return monthlyHigh; }
    public void setMonthlyHigh(String s) { this.monthlyHigh = s; }
    public String getMonthlyLow() { return monthlyLow; }
    public void setMonthlyLow(String s) { this.monthlyLow = s; }
    public String getUpperLimit() { return upperLimit; }
    public void setUpperLimit(String s) { this.upperLimit = s; }
    public String getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(String s) { this.lowerLimit = s; }
    public String getMarketCap() { return marketCap; }
    public void setMarketCap(String s) { this.marketCap = s; }
    public String getCapital() { return capital; }
    public void setCapital(String s) { this.capital = s; }
    public String getPeRatio() { return peRatio; }
    public void setPeRatio(String s) { this.peRatio = s; }
    public String getPbRatio() { return pbRatio; }
    public void setPbRatio(String s) { this.pbRatio = s; }
    public String getFreeFloat() { return freeFloat; }
    public void setFreeFloat(String s) { this.freeFloat = s; }
    public String getForeignRatio() { return foreignRatio; }
    public void setForeignRatio(String s) { this.foreignRatio = s; }
    public String getVolatility() { return volatility; }
    public void setVolatility(String s) { this.volatility = s; }
    public String getNetProfit() { return netProfit; }
    public void setNetProfit(String s) { this.netProfit = s; }
    public String getCeo() { return ceo; }
    public void setCeo(String s) { this.ceo = s; }
    public String getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(String s) { this.employeeCount = s; }
    public String getFoundedDate() { return foundedDate; }
    public void setFoundedDate(String s) { this.foundedDate = s; }
    public String getIpoDate() { return ipoDate; }
    public void setIpoDate(String s) { this.ipoDate = s; }
    public String getSector() { return sector; }
    public void setSector(String s) { this.sector = s; }
    public String getAddress() { return address; }
    public void setAddress(String s) { this.address = s; }
    public String getCountry() { return country; }
    public void setCountry(String s) { this.country = s; }
    public String getDescription() { return description; }
    public void setDescription(String s) { this.description = s; }
    public List<ShareholderItem> getShareholders() { return shareholders; }
    public void setShareholders(List<ShareholderItem> s) { this.shareholders = s; }
}
