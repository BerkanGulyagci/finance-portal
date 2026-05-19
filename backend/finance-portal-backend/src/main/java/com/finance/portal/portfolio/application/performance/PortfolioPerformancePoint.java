package com.finance.portal.portfolio.application.performance;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PortfolioPerformancePoint {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    private BigDecimal marketValue;
    private BigDecimal totalCost;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    /** TWR-benzeri kümülatif dönem getirisi (%), seçilen aralık başına göre. */
    private BigDecimal periodGrowthPercent;

    public PortfolioPerformancePoint() {
    }

    public PortfolioPerformancePoint(LocalDate date,
                                   BigDecimal marketValue,
                                   BigDecimal totalCost,
                                   BigDecimal profitLoss,
                                   BigDecimal profitLossPercent) {
        this(date, marketValue, totalCost, profitLoss, profitLossPercent, BigDecimal.ZERO);
    }

    public PortfolioPerformancePoint(LocalDate date,
                                   BigDecimal marketValue,
                                   BigDecimal totalCost,
                                   BigDecimal profitLoss,
                                   BigDecimal profitLossPercent,
                                   BigDecimal periodGrowthPercent) {
        this.date = date;
        this.marketValue = marketValue;
        this.totalCost = totalCost;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
        this.periodGrowthPercent = periodGrowthPercent;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getProfitLoss() {
        return profitLoss;
    }

    public void setProfitLoss(BigDecimal profitLoss) {
        this.profitLoss = profitLoss;
    }

    public BigDecimal getProfitLossPercent() {
        return profitLossPercent;
    }

    public void setProfitLossPercent(BigDecimal profitLossPercent) {
        this.profitLossPercent = profitLossPercent;
    }

    public BigDecimal getPeriodGrowthPercent() {
        return periodGrowthPercent;
    }

    public void setPeriodGrowthPercent(BigDecimal periodGrowthPercent) {
        this.periodGrowthPercent = periodGrowthPercent;
    }
}
