package com.finance.portal.market.application.fx.model;

import java.util.List;

public class TcmbFxFeed {

    private String date;
    private List<TcmbFxCurrencyRow> currencies;

    public TcmbFxFeed() {
    }

    public TcmbFxFeed(String date, List<TcmbFxCurrencyRow> currencies) {
        this.date = date;
        this.currencies = currencies;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<TcmbFxCurrencyRow> getCurrencies() {
        return currencies;
    }

    public void setCurrencies(List<TcmbFxCurrencyRow> currencies) {
        this.currencies = currencies;
    }
}
