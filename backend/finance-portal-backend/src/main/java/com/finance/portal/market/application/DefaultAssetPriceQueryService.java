package com.finance.portal.market.application;

import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.common.domain.AssetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * İlk versiyonda sadece STOCK ve CRYPTO desteklenir.
 *
 * STOCK  → StockQueryService.getStockSummary(symbol) üzerinden Yahoo Finance
 * CRYPTO → CryptoMarketService.findBySymbol(symbol) üzerinden CoinGecko
 *           (market cap sıralamasında top 500 coin içinde case-insensitive symbol araması)
 */
@Service
public class DefaultAssetPriceQueryService implements AssetPriceQueryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssetPriceQueryService.class);

    private final StockQueryService stockQueryService;
    private final CryptoMarketService cryptoMarketService;

    public DefaultAssetPriceQueryService(StockQueryService stockQueryService,
                                         CryptoMarketService cryptoMarketService) {
        this.stockQueryService = stockQueryService;
        this.cryptoMarketService = cryptoMarketService;
    }

    @Override
    public AssetPriceSnapshot getCurrentPrice(AssetType assetType, String symbol) {
        if (assetType == null) {
            throw new IllegalArgumentException("assetType must not be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }

        return switch (assetType) {
            case STOCK   -> fetchStockPrice(symbol);
            case CRYPTO  -> fetchCryptoPrice(symbol);
            default      -> throw new UnsupportedOperationException(
                    "Live price not supported for assetType: " + assetType);
        };
    }

    // -------------------------------------------------------------------------
    // STOCK
    // -------------------------------------------------------------------------

    private AssetPriceSnapshot fetchStockPrice(String symbol) {
        log.debug("Fetching stock price for symbol: {}", symbol);

        StockSummary summary = stockQueryService.getStockSummary(symbol.toUpperCase());

        LocalDateTime asOf = summary.getAsOf() != null
                ? LocalDateTime.parse(summary.getAsOf())
                : LocalDateTime.now();

        return new AssetPriceSnapshot(
                AssetType.STOCK,
                summary.getSymbol(),
                summary.getPrice(),
                summary.getCurrency(),
                asOf
        );
    }

    // -------------------------------------------------------------------------
    // CRYPTO
    // -------------------------------------------------------------------------

    private AssetPriceSnapshot fetchCryptoPrice(String symbol) {
        log.debug("Fetching crypto price for symbol: {}", symbol);

        // findBySymbol: market cap top 500 içinde case-insensitive arama yapar,
        // bulamazsa ResourceNotFoundException fırlatır.
        CryptoMarketItem match = cryptoMarketService.findBySymbol(symbol);

        return new AssetPriceSnapshot(
                AssetType.CRYPTO,
                match.getSymbol().toUpperCase(),
                match.getCurrentPrice(),
                "TRY",
                parseLastUpdated(match.getLastUpdated())
        );
    }

    /**
     * CoinGecko lastUpdated alanı ISO-8601 formatındadır (örn: "2026-03-08T23:08:21.063Z").
     * Parse başarısız olursa şimdiki zaman kullanılır.
     */
    private LocalDateTime parseLastUpdated(String lastUpdated) {
        if (lastUpdated == null || lastUpdated.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return java.time.OffsetDateTime
                    .parse(lastUpdated)
                    .toLocalDateTime();
        } catch (Exception ex) {
            log.warn("Failed to parse crypto lastUpdated '{}', using now()", lastUpdated);
            return LocalDateTime.now();
        }
    }
}
