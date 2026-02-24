package com.finance.portal.market.presentation.dto;

import java.util.List;

public class FxLatestResponse {

    private String provider;
    private String sourceType;
    private String base;
    private String asOf;
    private List<FxRateItemDto> rates;

    public FxLatestResponse() {
    }

    public FxLatestResponse(String provider, String sourceType, String base, String asOf, List<FxRateItemDto> rates) {
        this.provider = provider;
        this.sourceType = sourceType;
        this.base = base;
        this.asOf = asOf;
        this.rates = rates;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getAsOf() {
        return asOf;
    }

    public void setAsOf(String asOf) {
        this.asOf = asOf;
    }

    public List<FxRateItemDto> getRates() {
        return rates;
    }

    public void setRates(List<FxRateItemDto> rates) {
        this.rates = rates;
    }
}
