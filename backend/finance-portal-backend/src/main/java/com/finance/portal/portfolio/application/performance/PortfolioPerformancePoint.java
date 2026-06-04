package com.finance.portal.portfolio.application.performance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PortfolioPerformancePoint {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    private BigDecimal marketValue;
    private BigDecimal totalCost;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    /** TWR-benzeri kümülatif dönem getirisi (%), seçilen aralık başına göre. */
    private BigDecimal periodGrowthPercent;
    /**
     * Enflasyon (TÜFE) referans çizgisi: ilk noktanın piyasa değeri, o tarihten bu güne TÜFE
     * endeksiyle büyütülmüş hali. "Param sadece enflasyon kadar değerlenseydi" benchmark'ı.
     * TÜFE verisi yoksa null.
     */
    private BigDecimal inflationValue;

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
}
