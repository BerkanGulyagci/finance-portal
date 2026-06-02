package com.finance.portal.market.application.index;

import java.math.BigDecimal;

/**
 * BIST endeks listesi satırı — kod, ad, kategori + Yahoo'dan canlı fiyat/değişim.
 * Hacim/piyasa değeri YOK (Yahoo endeksler için vermez: regularMarketVolume=0, marketCap alanı yok).
 */
public class IndexSummary {

    private String code;          // XU100
    private String symbol;        // XU100.IS (Yahoo)
    private String name;          // BIST 100
    private String category;      // Ana | Sektör | Katılım | Tema
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private String currency;      // TRY

    public IndexSummary() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public BigDecimal getDayHigh() { return dayHigh; }
    public void setDayHigh(BigDecimal dayHigh) { this.dayHigh = dayHigh; }

    public BigDecimal getDayLow() { return dayLow; }
    public void setDayLow(BigDecimal dayLow) { this.dayLow = dayLow; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
