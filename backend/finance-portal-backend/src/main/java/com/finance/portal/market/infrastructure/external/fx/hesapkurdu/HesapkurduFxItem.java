package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Hesapkurdu getExchangeRates endpoint'inden gelen response.data[] içindeki
 * tek bir döviz kaydını temsil eder.
 *
 * exchange değerleri: "Bank" | "TCMB" | "SPOT" | "ExchOffice"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HesapkurduFxItem {

    private String fxId;
    private String code;
    private String shortCode;
    private String displayName;
    private String exchange;
    private String exchangeDisplayName;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal last;
    private BigDecimal dailyChange;
    private BigDecimal dailyChangePercent;
    private Long dateTime;
    private Integer bankId;

    public HesapkurduFxItem() {}

    public String getFxId()                        { return fxId; }
    public void setFxId(String fxId)               { this.fxId = fxId; }

    public String getCode()                        { return code; }
    public void setCode(String code)               { this.code = code; }

    public String getShortCode()                   { return shortCode; }
    public void setShortCode(String shortCode)     { this.shortCode = shortCode; }

    public String getDisplayName()                 { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getExchange()                    { return exchange; }
    public void setExchange(String exchange)       { this.exchange = exchange; }

    public String getExchangeDisplayName()                         { return exchangeDisplayName; }
    public void setExchangeDisplayName(String exchangeDisplayName) { this.exchangeDisplayName = exchangeDisplayName; }

    public BigDecimal getBid()                     { return bid; }
    public void setBid(BigDecimal bid)             { this.bid = bid; }

    public BigDecimal getAsk()                     { return ask; }
    public void setAsk(BigDecimal ask)             { this.ask = ask; }

    public BigDecimal getLast()                    { return last; }
    public void setLast(BigDecimal last)           { this.last = last; }

    public BigDecimal getDailyChange()                     { return dailyChange; }
    public void setDailyChange(BigDecimal dailyChange)     { this.dailyChange = dailyChange; }

    public BigDecimal getDailyChangePercent()                          { return dailyChangePercent; }
    public void setDailyChangePercent(BigDecimal dailyChangePercent)   { this.dailyChangePercent = dailyChangePercent; }

    public Long getDateTime()                      { return dateTime; }
    public void setDateTime(Long dateTime)         { this.dateTime = dateTime; }

    public Integer getBankId()                     { return bankId; }
    public void setBankId(Integer bankId)          { this.bankId = bankId; }
}
