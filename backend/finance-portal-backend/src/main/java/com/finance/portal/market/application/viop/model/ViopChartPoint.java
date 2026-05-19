package com.finance.portal.market.application.viop.model;

import java.math.BigDecimal;

public class ViopChartPoint {

    private Long timestamp;
    private String dateTime;
    private BigDecimal value;

    public ViopChartPoint() {
    }

    public ViopChartPoint(Long timestamp, String dateTime, BigDecimal value) {
        this.timestamp = timestamp;
        this.dateTime = dateTime;
        this.value = value;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }
}
