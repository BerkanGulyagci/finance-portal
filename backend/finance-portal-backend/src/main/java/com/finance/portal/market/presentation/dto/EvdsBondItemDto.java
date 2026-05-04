package com.finance.portal.market.presentation.dto;

import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * EVDS DİBS kıymet liste ve detay response DTO'su.
 * Tarih alanları ISO string olarak serialize edilir (yyyy-MM-dd).
 */
public class EvdsBondItemDto {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String instrumentCode;
    private String type;
    private String issueDate;
    private String maturityDate;
    private Integer remainingDays;
    private BigDecimal indicatorValue;
    private BigDecimal previousValue;
    private BigDecimal dailyChange;
    private BigDecimal dailyChangePercent;
    private BigDecimal couponRate;
    private String source;
    private String lastUpdated;

    public EvdsBondItemDto() {}

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Domain modelinden DTO oluşturur.
     */
    public static EvdsBondItemDto from(EvdsBondInstrument instrument) {
        EvdsBondItemDto dto = new EvdsBondItemDto();
        dto.instrumentCode      = instrument.getInstrumentCode();
        dto.type                = instrument.getType();
        dto.issueDate           = instrument.getIssueDate()    != null ? instrument.getIssueDate().format(ISO)    : null;
        dto.maturityDate        = instrument.getMaturityDate() != null ? instrument.getMaturityDate().format(ISO) : null;
        dto.remainingDays       = instrument.getRemainingDays();
        dto.indicatorValue      = instrument.getIndicatorValue();
        dto.previousValue       = instrument.getPreviousValue();
        dto.dailyChange         = instrument.getDailyChange();
        dto.dailyChangePercent  = instrument.getDailyChangePercent();
        dto.couponRate          = instrument.getCouponRate();
        dto.source              = instrument.getSource();
        dto.lastUpdated         = instrument.getLastUpdated() != null ? instrument.getLastUpdated().format(ISO) : null;
        return dto;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getInstrumentCode()     { return instrumentCode; }
    public String getType()               { return type; }
    public String getIssueDate()          { return issueDate; }
    public String getMaturityDate()       { return maturityDate; }
    public Integer getRemainingDays()     { return remainingDays; }
    public BigDecimal getIndicatorValue() { return indicatorValue; }
    public BigDecimal getPreviousValue()  { return previousValue; }
    public BigDecimal getDailyChange()    { return dailyChange; }
    public BigDecimal getDailyChangePercent() { return dailyChangePercent; }
    public BigDecimal getCouponRate()     { return couponRate; }
    public String getSource()             { return source; }
    public String getLastUpdated()        { return lastUpdated; }
}
