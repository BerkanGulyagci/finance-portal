package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.util.List;

public class FundHistoryResponse implements Serializable {

    private String code;
    private String period;
    private List<FundHistoryPoint> points;

    public FundHistoryResponse() {}

    public FundHistoryResponse(String code, String period, List<FundHistoryPoint> points) {
        this.code = code;
        this.period = period;
        this.points = points;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public List<FundHistoryPoint> getPoints() { return points; }
    public void setPoints(List<FundHistoryPoint> points) { this.points = points; }
}
