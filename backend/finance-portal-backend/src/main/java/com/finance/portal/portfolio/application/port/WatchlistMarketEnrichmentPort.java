package com.finance.portal.portfolio.application.port;

import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;

/**
 * İzleme listesi kalemleri için canlı piyasa zenginleştirme portu.
 */
public interface WatchlistMarketEnrichmentPort {

    void enrich(WatchlistItemResponse response);
}
