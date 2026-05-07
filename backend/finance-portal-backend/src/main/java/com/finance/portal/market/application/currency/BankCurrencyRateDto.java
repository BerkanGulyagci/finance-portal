package com.finance.portal.market.application.currency;

import java.io.Serializable;

/**
 * Banka döviz kuru DTO'su.
 * Hesapkurdu "Bank" exchange tipindeki kayıtlardan üretilir.
 * Redis cache'e serileştirilebilir olması için Serializable implement eder.
 *
 * Kur değerleri Double olarak tutulur — BigDecimal Redis GenericJackson2JsonRedisSerializer
 * ile deserialize edilirken sorun çıkardığından Double tercih edilmiştir.
 * sourceUpdatedAt ISO-8601 string olarak tutulur.
 */
public class BankCurrencyRateDto implements Serializable {

    private String bankName;
    private Integer bankId;
    private String currencyCode;
    private String currencyName;
    private Double buyRate;
    private Double sellRate;
    private Double lastRate;
    private Double spread;
    private Double dailyChange;
    private Double dailyChangePercent;
    /** ISO-8601 formatında tarih/saat: "2026-05-07T22:45:00Z" */
    private String sourceUpdatedAt;
    private String source;

    public BankCurrencyRateDto() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getBankName()                        { return bankName; }
    public void setBankName(String bankName)           { this.bankName = bankName; }

    public Integer getBankId()                         { return bankId; }
    public void setBankId(Integer bankId)              { this.bankId = bankId; }

    public String getCurrencyCode()                    { return currencyCode; }
    public void setCurrencyCode(String currencyCode)   { this.currencyCode = currencyCode; }

    public String getCurrencyName()                    { return currencyName; }
    public void setCurrencyName(String currencyName)   { this.currencyName = currencyName; }

    public Double getBuyRate()                         { return buyRate; }
    public void setBuyRate(Double buyRate)             { this.buyRate = buyRate; }

    public Double getSellRate()                        { return sellRate; }
    public void setSellRate(Double sellRate)           { this.sellRate = sellRate; }

    public Double getLastRate()                        { return lastRate; }
    public void setLastRate(Double lastRate)           { this.lastRate = lastRate; }

    public Double getSpread()                          { return spread; }
    public void setSpread(Double spread)               { this.spread = spread; }

    public Double getDailyChange()                     { return dailyChange; }
    public void setDailyChange(Double dailyChange)     { this.dailyChange = dailyChange; }

    public Double getDailyChangePercent()                          { return dailyChangePercent; }
    public void setDailyChangePercent(Double dailyChangePercent)   { this.dailyChangePercent = dailyChangePercent; }

    public String getSourceUpdatedAt()                        { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(String sourceUpdatedAt)    { this.sourceUpdatedAt = sourceUpdatedAt; }

    public String getSource()                          { return source; }
    public void setSource(String source)               { this.source = source; }
}
