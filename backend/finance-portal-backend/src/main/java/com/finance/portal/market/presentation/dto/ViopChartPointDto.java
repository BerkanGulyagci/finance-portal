package com.finance.portal.market.presentation.dto;

import java.math.BigDecimal;

/**
 * VİOP grafik veri noktası — frontend'e dönen DTO.
 */
public class ViopChartPointDto {

    /** Unix timestamp (milliseconds) */
    private Long timestamp;

    /** ISO-8601 formatında tarih/saat (örn: 2026-04-27T10:00:00) */
    private String dateTime;

    /** Sözleşme fiyatı */
    private BigDecimal value;

    public ViopChartPointDto() {}

    public ViopChartPointDto(Long timestamp, String dateTime, BigDecimal value) {
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
