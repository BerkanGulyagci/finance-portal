package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.util.List;

public class TefasFundHistoryResponse implements Serializable {
    private String code;
    private String range;
    private List<TefasFundHistoryPoint> points;

    public TefasFundHistoryResponse() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
    public List<TefasFundHistoryPoint> getPoints() { return points; }
    public void setPoints(List<TefasFundHistoryPoint> points) { this.points = points; }
}
