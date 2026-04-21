package com.finance.portal.market.application.bond;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BondItem implements Serializable {

    private String name;        // Kıymet Adı (TRB170626T13)
    private String maturityDate; // Vade (17.06.2026)
    private Integer daysToMaturity; // Vadeye Kalan Gün
    private String currency;    // Döviz (TL)
    private String buyPrice;    // Alış Fiyatı
    private String buyRate;     // Alış Oranı (%)
    private String sellPrice;   // Satış Fiyatı
    private String sellRate;    // Satış Oranı (%)

    public BondItem() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }
    public Integer getDaysToMaturity() { return daysToMaturity; }
    public void setDaysToMaturity(Integer daysToMaturity) { this.daysToMaturity = daysToMaturity; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getBuyPrice() { return buyPrice; }
    public void setBuyPrice(String buyPrice) { this.buyPrice = buyPrice; }
    public String getBuyRate() { return buyRate; }
    public void setBuyRate(String buyRate) { this.buyRate = buyRate; }
    public String getSellPrice() { return sellPrice; }
    public void setSellPrice(String sellPrice) { this.sellPrice = sellPrice; }
    public String getSellRate() { return sellRate; }
    public void setSellRate(String sellRate) { this.sellRate = sellRate; }
}
