package com.finance.portal.portfolio.application.performance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
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
}
