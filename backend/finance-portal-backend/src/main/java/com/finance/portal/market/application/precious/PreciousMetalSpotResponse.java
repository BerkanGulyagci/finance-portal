package com.finance.portal.market.application.precious;

import com.finance.portal.market.application.precious.model.PreciousMetalType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * GET /api/precious-metals/{metal}/spot response.
 * Platin ve Paladyum için.
 */
@Data
@NoArgsConstructor
public class PreciousMetalSpotResponse implements Serializable {

    private PreciousMetalType metalType;
    private String metalName;
    private String source;
    private boolean official;
    private boolean fallback;
    private boolean stale;
    private String lastUpdated;
    private String lastValidDate;
    private String disclaimer;
    private String dataSourceType;

    private BigDecimal usdOns;
    private BigDecimal tryKg;
    private BigDecimal tryGram;
    private BigDecimal eurOns;
}
