package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.WatchlistMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.watchlistenrich.BondWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.CommodityWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.CryptoWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.FundWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.FutureWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.FxWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.GoldWatchlistEnricher;
import com.finance.portal.portfolio.service.watchlistenrich.StockLikeWatchlistEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * İzleme listesi satırları için canlı piyasa alanlarını doldurur. Asset-type başına ayrı
 * {@code *WatchlistEnricher} bileşenleri vardır (paket {@code service/watchlistenrich}); bu sınıf
 * sadece dispatcher + tüm enrich akışı için ortak hata logu yayımlama görevini üstlenir.
 * Her enricher kendi spot fiyatını ve (varsa) 1Y kapanışlardan 52w/MA trend metriklerini kendi
 * içinde halleder; FUND ise trend metriklerini fon getirileri (1A/3A/YTD/1Y) ile temsil eder.
 */
@Service
public class PortfolioWatchlistMarketEnricher implements WatchlistMarketEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioWatchlistMarketEnricher.class);

    private final CryptoWatchlistEnricher cryptoWatchlistEnricher;
    private final FxWatchlistEnricher fxWatchlistEnricher;
    private final StockLikeWatchlistEnricher stockLikeWatchlistEnricher;
    private final FutureWatchlistEnricher futureWatchlistEnricher;
    private final BondWatchlistEnricher bondWatchlistEnricher;
    private final GoldWatchlistEnricher goldWatchlistEnricher;
    private final CommodityWatchlistEnricher commodityWatchlistEnricher;
    private final FundWatchlistEnricher fundWatchlistEnricher;

    public PortfolioWatchlistMarketEnricher(CryptoWatchlistEnricher cryptoWatchlistEnricher,
                                            FxWatchlistEnricher fxWatchlistEnricher,
                                            StockLikeWatchlistEnricher stockLikeWatchlistEnricher,
                                            FutureWatchlistEnricher futureWatchlistEnricher,
                                            BondWatchlistEnricher bondWatchlistEnricher,
                                            GoldWatchlistEnricher goldWatchlistEnricher,
                                            CommodityWatchlistEnricher commodityWatchlistEnricher,
                                            FundWatchlistEnricher fundWatchlistEnricher) {
        this.cryptoWatchlistEnricher = cryptoWatchlistEnricher;
        this.fxWatchlistEnricher = fxWatchlistEnricher;
        this.stockLikeWatchlistEnricher = stockLikeWatchlistEnricher;
        this.futureWatchlistEnricher = futureWatchlistEnricher;
        this.bondWatchlistEnricher = bondWatchlistEnricher;
        this.goldWatchlistEnricher = goldWatchlistEnricher;
        this.commodityWatchlistEnricher = commodityWatchlistEnricher;
        this.fundWatchlistEnricher = fundWatchlistEnricher;
    }

    @Override
    public void enrich(WatchlistItemResponse r) {
        try {
            AssetType type = r.getAssetType();
            String symbol = r.getSymbol();
            if (type == null || symbol == null || symbol.isBlank()) {
                return;
            }
            switch (type) {
                case CRYPTO -> cryptoWatchlistEnricher.enrich(r, symbol);
                case STOCK -> stockLikeWatchlistEnricher.enrich(r, symbol);
                case FUTURE -> futureWatchlistEnricher.enrich(r, symbol);
                case FUND -> fundWatchlistEnricher.enrich(r, symbol);
                case FX -> fxWatchlistEnricher.enrich(r, symbol);
                case GOLD -> goldWatchlistEnricher.enrich(r, symbol);
                case COMMODITY -> commodityWatchlistEnricher.enrich(r, symbol);
                case BOND -> bondWatchlistEnricher.enrich(r, symbol);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Watchlist live price not supported for assetType={} symbol={}",
                    r.getAssetType(), r.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to enrich watchlist item assetType={} symbol={}: {}",
                    r.getAssetType(), r.getSymbol(), ex.getMessage());
        }
    }
}
