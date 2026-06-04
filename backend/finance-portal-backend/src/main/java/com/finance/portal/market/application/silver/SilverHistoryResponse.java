package com.finance.portal.market.application.silver;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * GET /api/v1/silver/history response.
 */
@Data
@NoArgsConstructor
public class SilverHistoryResponse implements Serializable {

    private String symbol;
    private String range;
    private String currency;
    private String source;
    private boolean official;
    private boolean fallback;
    private boolean stale;
    private String lastUpdated;
    private String disclaimer;

    private List<SilverHistoryPoint> points;
}
