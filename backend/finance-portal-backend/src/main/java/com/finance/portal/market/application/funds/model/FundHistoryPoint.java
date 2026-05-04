package com.finance.portal.market.application.funds.model;

import java.io.Serializable;

/**
 * Normalize edilmiş fon tarihsel fiyat noktası.
 * Frontend bu formata bağımlıdır — HangiKredi response formatından bağımsız.
 *
 * HangiKredi mapping:
 *   dateTime  → date       ("2023-08-16")
 *   dateText  → dateText   ("16/08/2023")
 *   last      → price
 *   changePercent         → changePercent
 *   dailyChangeAmount     → dailyChangeAmount
 *   dailyChangePercent    → dailyChangePercent
 */
public class FundHistoryPoint implements Serializable {

    private String date;               // "2023-08-16"
    private String dateText;           // "16/08/2023"
    private double price;              // last
    private double changePercent;      // kümülatif değişim %
    private double dailyChangeAmount;  // günlük değişim TL
    private double dailyChangePercent; // günlük değişim %

    public FundHistoryPoint() {}

    public FundHistoryPoint(String date, String dateText, double price,
                            double changePercent, double dailyChangeAmount, double dailyChangePercent) {
        this.date = date;
        this.dateText = dateText;
        this.price = price;
        this.changePercent = changePercent;
        this.dailyChangeAmount = dailyChangeAmount;
        this.dailyChangePercent = dailyChangePercent;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getDateText() { return dateText; }
    public void setDateText(String dateText) { this.dateText = dateText; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    public double getDailyChangeAmount() { return dailyChangeAmount; }
    public void setDailyChangeAmount(double dailyChangeAmount) { this.dailyChangeAmount = dailyChangeAmount; }
    public double getDailyChangePercent() { return dailyChangePercent; }
    public void setDailyChangePercent(double dailyChangePercent) { this.dailyChangePercent = dailyChangePercent; }
}
