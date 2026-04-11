package com.finance.portal.market.application.ipo;

import java.io.Serializable;

public class IpoItem implements Serializable {

    private String ticker;
    private String name;
    private String date;
    private String url;

    public IpoItem() {}

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
