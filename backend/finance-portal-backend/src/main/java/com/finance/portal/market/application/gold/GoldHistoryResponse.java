package com.finance.portal.market.application.gold;

import java.io.Serializable;
import java.util.List;

public class GoldHistoryResponse implements Serializable {
    private String symbol;
    private String range;
    private String currency;
    private List<GoldHistoryPoint> points;

    public GoldHistoryResponse() {}

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<GoldHistoryPoint> getPoints() { return points; }
    public void setPoints(List<GoldHistoryPoint> points) { this.points = points; }
}
