package com.finance.portal.market.application.viop.model;

import java.math.BigDecimal;

public class ViopContractDetail {

    private String name;
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private Long openPositionCount;
    private Long openPositionChange;
    private BigDecimal settlementPrice;
    private BigDecimal prevSettlementPrice;
    private String time;
    private String tradingViewSymbol;

    public ViopContractDetail() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public Long getOpenPositionCount() {
        return openPositionCount;
    }

    public void setOpenPositionCount(Long openPositionCount) {
        this.openPositionCount = openPositionCount;
    }

    public Long getOpenPositionChange() {
        return openPositionChange;
    }

    public void setOpenPositionChange(Long openPositionChange) {
        this.openPositionChange = openPositionChange;
    }

    public BigDecimal getSettlementPrice() {
        return settlementPrice;
    }

    public void setSettlementPrice(BigDecimal settlementPrice) {
        this.settlementPrice = settlementPrice;
    }

    public BigDecimal getPrevSettlementPrice() {
        return prevSettlementPrice;
    }

    public void setPrevSettlementPrice(BigDecimal prevSettlementPrice) {
        this.prevSettlementPrice = prevSettlementPrice;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTradingViewSymbol() {
        return tradingViewSymbol;
    }

    public void setTradingViewSymbol(String tradingViewSymbol) {
        this.tradingViewSymbol = tradingViewSymbol;
    }
}
