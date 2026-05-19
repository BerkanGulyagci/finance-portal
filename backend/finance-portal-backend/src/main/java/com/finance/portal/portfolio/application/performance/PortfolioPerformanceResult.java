package com.finance.portal.portfolio.application.performance;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortfolioPerformanceResult {

    private UUID portfolioId;
    private String range;
    private String metric;
    private String currency;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    private List<PortfolioPerformancePoint> points = new ArrayList<>();
    private List<ExcludedPerformanceAsset> excludedAssets = new ArrayList<>();

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(UUID portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<PortfolioPerformancePoint> getPoints() {
        return points;
    }

    public void setPoints(List<PortfolioPerformancePoint> points) {
        this.points = points;
    }

    public List<ExcludedPerformanceAsset> getExcludedAssets() {
        return excludedAssets;
    }

    public void setExcludedAssets(List<ExcludedPerformanceAsset> excludedAssets) {
        this.excludedAssets = excludedAssets;
    }
}
