package com.finance.portal.market.application;

import com.finance.portal.common.infrastructure.exception.ResourceNotFoundException;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import com.finance.portal.common.domain.AssetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Desteklenen asset type'lar ve veri kaynakları:
 * - STOCK   → Yahoo Finance (StockQueryService)
 * - CRYPTO  → CoinGecko (CryptoMarketService) — TRY bazlı
 * - FX      → TCMB (MarketFxService) — symbol: USD, EUR, GBP vb.
 * - FUND    → Yahoo Finance (StockQueryService) — ETF/fon (SPY, QQQ, GLD vb.)
 * - FUTURE  → Yahoo Finance (StockQueryService) — ES=F, GC=F vb.
 */
@Service
public class DefaultAssetPriceQueryService implements AssetPriceQueryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssetPriceQueryService.class);

    private final StockQueryService stockQueryService;
    private final CryptoMarketService cryptoMarketService;
    private final MarketFxService marketFxService;

    public DefaultAssetPriceQueryService(StockQueryService stockQueryService,
                                         CryptoMarketService cryptoMarketService,
                                         MarketFxService marketFxService) {
        this.stockQueryService   = stockQueryService;
        this.cryptoMarketService = cryptoMarketService;
        this.marketFxService     = marketFxService;
    }

    @Override
    public AssetPriceSnapshot getCurrentPrice(AssetType assetType, String symbol) {
        if (assetType == null) throw new IllegalArgumentException("assetType must not be null");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol must not be blank");

        return switch (assetType) {
            case STOCK   -> fetchStockPrice(symbol);
            case CRYPTO  -> fetchCryptoPrice(symbol);
            case FX      -> fetchFxPrice(symbol);
            case FUND    -> fetchFundPrice(symbol);
            case FUTURE  -> fetchFuturePrice(symbol);
        };
    }

    // ── STOCK ─────────────────────────────────────────────────────────────────
    private AssetPriceSnapshot fetchStockPrice(String symbol) {
        log.debug("Fetching stock price for symbol: {}", symbol);
        StockSummary summary = stockQueryService.getStockSummary(symbol.toUpperCase());
        return new AssetPriceSnapshot(AssetType.STOCK, summary.getSymbol(),
                summary.getPrice(), summary.getCurrency(), parseDateTime(summary.getAsOf()));
    }

    // ── CRYPTO ────────────────────────────────────────────────────────────────
    private AssetPriceSnapshot fetchCryptoPrice(String symbol) {
        log.debug("Fetching crypto price for symbol: {}", symbol);
        CryptoMarketItem match = cryptoMarketService.findBySymbol(symbol);
        return new AssetPriceSnapshot(AssetType.CRYPTO, match.getSymbol().toUpperCase(),
                match.getCurrentPrice(), "TRY", parseLastUpdated(match.getLastUpdated()));
    }

    // ── FX ────────────────────────────────────────────────────────────────────
    /**
     * TCMB'den döviz kuru çeker.
     * symbol: "USD", "EUR", "GBP" vb. (TRY karşılığı satış kuru döner)
     * Birim 1'den farklıysa (örn. JPY=100) fiyat normalize edilir.
     */
    private AssetPriceSnapshot fetchFxPrice(String symbol) {
        log.debug("Fetching FX price for symbol: {}", symbol);
        String upperSymbol = symbol.toUpperCase();

        FxLatestResponse fxData = marketFxService.getTcmbLatestRates(null);
        FxRateItemDto rate = fxData.getRates().stream()
                .filter(r -> upperSymbol.equals(r.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FX rate not found for symbol: " + upperSymbol));

        BigDecimal price = rate.getSell();
        if (rate.getUnit() > 1) {
            price = price.divide(BigDecimal.valueOf(rate.getUnit()), 6, RoundingMode.HALF_UP);
        }

        return new AssetPriceSnapshot(AssetType.FX, upperSymbol, price, "TRY", LocalDateTime.now());
    }

    // ── FUND ──────────────────────────────────────────────────────────────────
    /**
     * Yahoo Finance'den ETF/global fon fiyatı çeker.
     * symbol: "SPY", "QQQ", "GLD" vb.
     * ETF'ler Yahoo'da hisse gibi chart endpoint'i üzerinden çalışır.
     */
    private AssetPriceSnapshot fetchFundPrice(String symbol) {
        log.debug("Fetching fund price for symbol: {}", symbol);
        StockSummary summary = stockQueryService.getStockSummary(symbol.toUpperCase());
        return new AssetPriceSnapshot(AssetType.FUND, summary.getSymbol(),
                summary.getPrice(), summary.getCurrency(), parseDateTime(summary.getAsOf()));
    }

    // ── FUTURE ────────────────────────────────────────────────────────────────
    /**
     * Yahoo Finance'den vadeli kontrat fiyatı çeker.
     * symbol: "ES=F", "GC=F", "CL=F" vb.
     */
    private AssetPriceSnapshot fetchFuturePrice(String symbol) {
        log.debug("Fetching future price for symbol: {}", symbol);
        StockSummary summary = stockQueryService.getStockSummary(symbol.toUpperCase());
        return new AssetPriceSnapshot(AssetType.FUTURE, summary.getSymbol(),
                summary.getPrice(), summary.getCurrency(), parseDateTime(summary.getAsOf()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try { return LocalDateTime.parse(value); } catch (Exception e) { return LocalDateTime.now(); }
    }

    private LocalDateTime parseLastUpdated(String lastUpdated) {
        if (lastUpdated == null || lastUpdated.isBlank()) return LocalDateTime.now();
        try {
            return java.time.OffsetDateTime.parse(lastUpdated).toLocalDateTime();
        } catch (Exception ex) {
            log.warn("Failed to parse crypto lastUpdated '{}', using now()", lastUpdated);
            return LocalDateTime.now();
        }
    }
}
