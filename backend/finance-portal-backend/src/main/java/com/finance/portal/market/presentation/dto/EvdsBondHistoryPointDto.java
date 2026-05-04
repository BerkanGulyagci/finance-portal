package com.finance.portal.market.presentation.dto;

import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * EVDS DİBS tarihsel veri noktası response DTO'su.
 */
public class EvdsBondHistoryPointDto {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String date;
    private String dateText;
    private String instrumentCode;
    private BigDecimal indicatorValue;

    public EvdsBondHistoryPointDto() {}

    // ── Factory ───────────────────────────────────────────────────────────

    public static EvdsBondHistoryPointDto from(EvdsBondHistoryPoint point) {
        EvdsBondHistoryPointDto dto = new EvdsBondHistoryPointDto();
        dto.date           = point.getDate() != null ? point.getDate().format(ISO) : null;
        dto.dateText       = point.getDateText();
        dto.instrumentCode = point.getInstrumentCode();
        dto.indicatorValue = point.getIndicatorValue();
        return dto;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getDate()               { return date; }
    public String getDateText()           { return dateText; }
    public String getInstrumentCode()     { return instrumentCode; }
    public BigDecimal getIndicatorValue() { return indicatorValue; }
}
