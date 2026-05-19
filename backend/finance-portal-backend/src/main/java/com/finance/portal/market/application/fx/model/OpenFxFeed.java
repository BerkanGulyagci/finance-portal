package com.finance.portal.market.application.fx.model;

import java.util.Map;

public class OpenFxFeed {

    private String baseCode;
    private String timeLastUpdateUtc;
    private Map<String, Double> rates;

    public OpenFxFeed() {
    }

    public OpenFxFeed(String baseCode, String timeLastUpdateUtc, Map<String, Double> rates) {
        this.baseCode = baseCode;
        this.timeLastUpdateUtc = timeLastUpdateUtc;
        this.rates = rates;
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
