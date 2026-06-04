package com.finance.portal.market.application.commodity;

import lombok.Data;

import java.util.List;

/**
 * /api/v1/commodities/history endpoint'i için wrapper response.
 */
@Data
public class CommodityHistoryResponse {

    private String symbol;
    private String displayNameTr;
    private String displayNameEn;
    private String category;
    private String unit;
    private String range;
    private String interval;
    private String displayCurrency;
    private String source;
    private String dataType;
    private String disclaimer;

    private List<CommodityHistoryPointDto> points;
}
