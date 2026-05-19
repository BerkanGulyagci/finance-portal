package com.finance.portal.portfolio.application.performance;

import com.finance.portal.common.domain.AssetType;

public class ExcludedPerformanceAsset {

    private String symbol;
    private AssetType assetType;
    private String reason;

    public ExcludedPerformanceAsset() {
    }

    public ExcludedPerformanceAsset(String symbol, AssetType assetType, String reason) {
        this.symbol = symbol;
        this.assetType = assetType;
        this.reason = reason;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
