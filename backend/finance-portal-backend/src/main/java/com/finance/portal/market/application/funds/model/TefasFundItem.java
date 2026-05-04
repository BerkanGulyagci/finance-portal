package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class TefasFundItem implements Serializable {

    private String code;
    private String title;
    private BigDecimal price;
    private BigDecimal dailyReturnPercent;
    private BigDecimal marketCap;
    private Long numberOfInvestors;
    private BigDecimal sharesInCirculation;
    private BigDecimal borsaBultenFiyat;
    private String date;
    private String updateDate;
    // Dönem getirileri
    private Double return1M;
    private Double return3M;
    private Double return6M;
    private Double return1Y;
    private Double return3Y;
    private Double return5Y;
    private Double dailyReturn;
    private Integer riskValue;
    private String kind;
    private String logoUrl;
    private Long hangiKrediId;  // HangiKredi internal fund id (grafik API için)
    // Detay alanları (HangiKredi'den)
    private String lastPrice;          // Son fiyat (örn: "35,215")
    private String changePercent;      // Günlük değişim % (örn: "%-0,20")
    private String changeAmount;       // Günlük değişim TL
    private java.util.List<FundInfoLabel> infoLabels;   // Fon bilgisi etiketleri
    private java.util.List<FundDistributionItem> distribution; // Fon içeriği
    private java.util.List<FundPerformanceItem> performanceComparison; // Geçmiş performans karşılaştırması
    private java.util.List<FundChartPoint> chartData;  // Grafik verisi

    public TefasFundItem() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getDailyReturnPercent() { return dailyReturnPercent; }
    public void setDailyReturnPercent(BigDecimal dailyReturnPercent) { this.dailyReturnPercent = dailyReturnPercent; }
    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }
    public Long getNumberOfInvestors() { return numberOfInvestors; }
    public void setNumberOfInvestors(Long numberOfInvestors) { this.numberOfInvestors = numberOfInvestors; }
    public BigDecimal getSharesInCirculation() { return sharesInCirculation; }
    public void setSharesInCirculation(BigDecimal sharesInCirculation) { this.sharesInCirculation = sharesInCirculation; }
    public BigDecimal getBorsaBultenFiyat() { return borsaBultenFiyat; }
    public void setBorsaBultenFiyat(BigDecimal borsaBultenFiyat) { this.borsaBultenFiyat = borsaBultenFiyat; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Double getReturn1M() { return return1M; }
    public void setReturn1M(Double return1M) { this.return1M = return1M; }
    public Double getReturn3M() { return return3M; }
    public void setReturn3M(Double return3M) { this.return3M = return3M; }
    public Double getReturn6M() { return return6M; }
    public void setReturn6M(Double return6M) { this.return6M = return6M; }
    public Double getReturn1Y() { return return1Y; }
    public void setReturn1Y(Double return1Y) { this.return1Y = return1Y; }
    public Double getReturn3Y() { return return3Y; }
    public void setReturn3Y(Double return3Y) { this.return3Y = return3Y; }
    public Double getReturn5Y() { return return5Y; }
    public void setReturn5Y(Double return5Y) { this.return5Y = return5Y; }
    public Double getDailyReturn() { return dailyReturn; }
    public void setDailyReturn(Double dailyReturn) { this.dailyReturn = dailyReturn; }
    public Integer getRiskValue() { return riskValue; }
    public void setRiskValue(Integer riskValue) { this.riskValue = riskValue; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Long getHangiKrediId() { return hangiKrediId; }
    public void setHangiKrediId(Long hangiKrediId) { this.hangiKrediId = hangiKrediId; }
    public String getLastPrice() { return lastPrice; }
    public void setLastPrice(String lastPrice) { this.lastPrice = lastPrice; }
    public String getChangePercent() { return changePercent; }
    public void setChangePercent(String changePercent) { this.changePercent = changePercent; }
    public String getChangeAmount() { return changeAmount; }
    public void setChangeAmount(String changeAmount) { this.changeAmount = changeAmount; }
    public String getUpdateDate() { return updateDate; }
    public void setUpdateDate(String updateDate) { this.updateDate = updateDate; }
    public java.util.List<FundInfoLabel> getInfoLabels() { return infoLabels; }
    public void setInfoLabels(java.util.List<FundInfoLabel> infoLabels) { this.infoLabels = infoLabels; }
    public java.util.List<FundDistributionItem> getDistribution() { return distribution; }
    public void setDistribution(java.util.List<FundDistributionItem> distribution) { this.distribution = distribution; }
    public java.util.List<FundPerformanceItem> getPerformanceComparison() { return performanceComparison; }
    public void setPerformanceComparison(java.util.List<FundPerformanceItem> performanceComparison) { this.performanceComparison = performanceComparison; }
    public java.util.List<FundChartPoint> getChartData() { return chartData; }
    public void setChartData(java.util.List<FundChartPoint> chartData) { this.chartData = chartData; }

    // ── İç sınıflar ──────────────────────────────────────────────────────────

    public static class FundInfoLabel implements Serializable {
        private String title;
        private String text;
        private int order;
        public FundInfoLabel() {}
        public FundInfoLabel(String title, String text, int order) { this.title = title; this.text = text; this.order = order; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
    }

    public static class FundDistributionItem implements Serializable {
        private String title;
        private double rate;
        private String rateText;
        public FundDistributionItem() {}
        public FundDistributionItem(String title, double rate, String rateText) { this.title = title; this.rate = rate; this.rateText = rateText; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
        public String getRateText() { return rateText; }
        public void setRateText(String rateText) { this.rateText = rateText; }
    }

    public static class FundPerformanceItem implements Serializable {
        private String code;
        private String name;
        private double changePercent;
        private String changePercentFormated;
        public FundPerformanceItem() {}
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        public String getChangePercentFormated() { return changePercentFormated; }
        public void setChangePercentFormated(String changePercentFormated) { this.changePercentFormated = changePercentFormated; }
    }

    public static class FundChartPoint implements Serializable {
        private String date;
        private String dateText;
        private double changePercent;
        private double last;
        public FundChartPoint() {}
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getDateText() { return dateText; }
        public void setDateText(String dateText) { this.dateText = dateText; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        public double getLast() { return last; }
        public void setLast(double last) { this.last = last; }
    }
}
