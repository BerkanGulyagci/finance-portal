package com.finance.portal.market.application.precious;

import com.finance.portal.market.application.precious.model.PreciousMetalType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * GET /api/v1/precious-metals/{metal}/history response.
 */
@Data
@NoArgsConstructor
public class PreciousMetalHistoryResponse implements Serializable {

    private PreciousMetalType metalType;
    private String metalName;
    private String range;
    private String currency;
    private String source;
    private boolean official;
    private boolean fallback;
    private String lastUpdated;
    private String disclaimer;
    private String dataSourceType;

    private List<PreciousMetalHistoryPoint> points;
}
