package com.finance.portal.portfolio.application.port;

import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;

/**
 * Portföy pozisyonları için canlı piyasa zenginleştirme portu.
 */
public interface HoldingMarketEnrichmentPort {

    void enrich(PortfolioHoldingResponse holding);
}
