package com.finance.portal.market.infrastructure.external.fx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class OpenErApiResponseDto {

    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    @JsonProperty("time_last_update_utc")
    private String timeLastUpdateUtc;

    @JsonProperty("rates")
    private Map<String, Double> rates;

    public OpenErApiResponseDto() {
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getBaseCode() {
        return baseCode;
    }

    public void setBaseCode(String baseCode) {
        this.baseCode = baseCode;
    }

    public String getTimeLastUpdateUtc() {
        return timeLastUpdateUtc;
    }

    public void setTimeLastUpdateUtc(String timeLastUpdateUtc) {
        this.timeLastUpdateUtc = timeLastUpdateUtc;
    }

    public Map<String, Double> getRates() {
        return rates;
    }

    public void setRates(Map<String, Double> rates) {
        this.rates = rates;
    }
}

