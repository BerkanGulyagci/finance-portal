package com.finance.portal.market.application.bond.evds;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVDS'den normalize edilmiş DİBS kıymet modeli.
 * Liste ve detay sayfası için ortak domain nesnesi.
 */
public class EvdsBondInstrument {

    /** Kıymet kodu — örn. TRD070727K10 */
    private String instrumentCode;

    /** Tür — "Hazine Bonosu", "Devlet Tahvili", "DİBS" */
    private String type;

    /** İhraç tarihi (EVDS START_DATE) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    /** Vade tarihi (EVDS END_DATE) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;

    /** Vadeye kalan gün (maturityDate - today) */
    private int remainingDays;

    /** TCMB EVDS Gösterge Değeri — en güncel değer */
    private BigDecimal indicatorValue;

    /** Bir önceki iş günü değeri */
    private BigDecimal previousValue;

    /** Günlük değişim = indicatorValue - previousValue */
    private BigDecimal dailyChange;

    /** Günlük değişim yüzdesi = dailyChange / previousValue * 100 */
    private BigDecimal dailyChangePercent;

    /** Kupon faiz oranı — .ORAN serisinden, yoksa null */
    private BigDecimal couponRate;

    /** Veri kaynağı — sabit "TCMB EVDS" */
    private String source;

    /** Son güncelleme tarihi */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastUpdated;

    public EvdsBondInstrument() {}

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public int getRemainingDays() { return remainingDays; }
    public void setRemainingDays(int remainingDays) { this.remainingDays = remainingDays; }

    public BigDecimal getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(BigDecimal indicatorValue) { this.indicatorValue = indicatorValue; }

    public BigDecimal getPreviousValue() { return previousValue; }
    public void setPreviousValue(BigDecimal previousValue) { this.previousValue = previousValue; }

    public BigDecimal getDailyChange() { return dailyChange; }
    public void setDailyChange(BigDecimal dailyChange) { this.dailyChange = dailyChange; }

    public BigDecimal getDailyChangePercent() { return dailyChangePercent; }
    public void setDailyChangePercent(BigDecimal dailyChangePercent) { this.dailyChangePercent = dailyChangePercent; }

    public BigDecimal getCouponRate() { return couponRate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDate getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDate lastUpdated) { this.lastUpdated = lastUpdated; }
}
