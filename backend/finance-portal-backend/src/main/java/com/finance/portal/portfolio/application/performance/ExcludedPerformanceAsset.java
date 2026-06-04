package com.finance.portal.portfolio.application.performance;

import com.finance.portal.common.domain.AssetType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExcludedPerformanceAsset {

    private String symbol;
    private AssetType assetType;
    private String reason;
}
