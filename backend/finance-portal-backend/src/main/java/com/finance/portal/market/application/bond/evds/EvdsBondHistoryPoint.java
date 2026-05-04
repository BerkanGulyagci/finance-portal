package com.finance.portal.market.application.bond.evds;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarihsel grafik için tek bir günlük EVDS gösterge değeri noktası.
 */
public class EvdsBondHistoryPoint {

    /** Tarih — ISO format (yyyy-MM-dd) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** Tarih — görüntüleme formatı (dd-MM-yyyy) */
    private String dateText;

    /** Kıymet kodu */
    private String instrumentCode;

    /** TCMB EVDS Gösterge Değeri */
    private BigDecimal indicatorValue;

    public EvdsBondHistoryPoint() {}

    public EvdsBondHistoryPoint(LocalDate date, String dateText, String instrumentCode, BigDecimal indicatorValue) {
        this.date = date;
        this.dateText = dateText;
        this.instrumentCode = instrumentCode;
        this.indicatorValue = indicatorValue;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getDateText() { return dateText; }
    public void setDateText(String dateText) { this.dateText = dateText; }

    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }

    public BigDecimal getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(BigDecimal indicatorValue) { this.indicatorValue = indicatorValue; }
}
