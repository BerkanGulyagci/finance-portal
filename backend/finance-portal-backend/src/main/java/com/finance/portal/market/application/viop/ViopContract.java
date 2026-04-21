package com.finance.portal.market.application.viop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ViopContract implements Serializable {

    private String name;
    private String changePercent;
    private String lastPrice;
    private String high;
    private String low;
    private String openPositionCount;
    private String openPositionChange;
    private String settlementPrice;
    private String prevSettlementPrice;
    private String time;

    public ViopContract() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getChangePercent() { return changePercent; }
    public void setChangePercent(String changePercent) { this.changePercent = changePercent; }
    public String getLastPrice() { return lastPrice; }
    public void setLastPrice(String lastPrice) { this.lastPrice = lastPrice; }
    public String getHigh() { return high; }
    public void setHigh(String high) { this.high = high; }
    public String getLow() { return low; }
    public void setLow(String low) { this.low = low; }
    public String getOpenPositionCount() { return openPositionCount; }
    public void setOpenPositionCount(String openPositionCount) { this.openPositionCount = openPositionCount; }
    public String getOpenPositionChange() { return openPositionChange; }
    public void setOpenPositionChange(String openPositionChange) { this.openPositionChange = openPositionChange; }
    public String getSettlementPrice() { return settlementPrice; }
    public void setSettlementPrice(String settlementPrice) { this.settlementPrice = settlementPrice; }
    public String getPrevSettlementPrice() { return prevSettlementPrice; }
    public void setPrevSettlementPrice(String prevSettlementPrice) { this.prevSettlementPrice = prevSettlementPrice; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
