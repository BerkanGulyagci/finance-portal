package com.finance.portal.market.application.funds.model;

import java.util.List;

/**
 * Fon fiyat geçmişi yanıtı (grafik için). Kaynak: TEFAS resmi API.
 */
public class FundPriceHistoryResponse {

    private String code;
    private String range;
    private String source;
    private List<FundPriceHistoryPoint> points;

    public FundPriceHistoryResponse() {
    }

    public FundPriceHistoryResponse(String code, String range, String source, List<FundPriceHistoryPoint> points) {
        this.code = code;
        this.range = range;
        this.source = source;
        this.points = points;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<FundPriceHistoryPoint> getPoints() { return points; }
    public void setPoints(List<FundPriceHistoryPoint> points) { this.points = points; }
}
