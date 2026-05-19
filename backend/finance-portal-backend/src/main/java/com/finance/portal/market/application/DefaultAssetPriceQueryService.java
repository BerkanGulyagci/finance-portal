package com.finance.portal.market.application;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.common.domain.AssetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Desteklenen asset type'lar ve veri kaynakları:
 * - STOCK   → Yahoo Finance (StockQueryService)
 * - CRYPTO  → CoinGecko (CryptoMarketService) — TRY bazlı
 * - FX      → TCMB (MarketFxService) — symbol: USD, EUR, GBP vb.
 * - FUND    → Rasyonet (TEFAS/BES/OKS listeleri + card detayı)
 * - FUTURE  → Yahoo Finance (StockQueryService) — ES=F, GC=F vb.
 */
@Service
public class DefaultAssetPriceQueryService implements AssetPriceQueryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssetPriceQueryService.class);

    private final StockQueryService stockQueryService;
    private final CryptoMarketService cryptoMarketService;
    private final MarketFxService marketFxService;
    private final RasyonetFundService rasyonetFundService;

    public DefaultAssetPriceQueryService(StockQueryService stockQueryService,
                                         CryptoMarketService cryptoMarketService,
                                         MarketFxService marketFxService,
                                         RasyonetFundService rasyonetFundService) {
        this.stockQueryService   = stockQueryService;
        this.cryptoMarketService = cryptoMarketService;
        this.marketFxService     = marketFxService;
        this.rasyonetFundService = rasyonetFundService;
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
            case GOLD, COMMODITY, BOND -> throw new UnsupportedOperationException(
                    "Live price is not supported for assetType=" + assetType + " symbol=" + symbol);
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

        FxLatestRates fxData = marketFxService.getTcmbLatestRates(null);
        FxRateItem rate = fxData.getRates().stream()
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
     * TEFAS/BES/OKS fon kodları için Rasyonet (liste + gerekirse card).
     */
    private AssetPriceSnapshot fetchFundPrice(String symbol) {
        log.debug("Fetching fund price from Rasyonet for symbol: {}", symbol);
        String code = symbol.trim().toUpperCase();

        RasyonetFundDto listed = findRasyonetFundByCode(code);
        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return new AssetPriceSnapshot(AssetType.FUND, code, listed.getPrice(),
                    "TRY", LocalDateTime.now());
        }

        for (String sc : List.of("TMF", "TPF", "TAF")) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                String cur = d.getCurrencyCode();
                String currency = cur != null && !cur.isBlank() ? cur : "TRY";
                return new AssetPriceSnapshot(AssetType.FUND, code, d.getPrice(),
                        currency, LocalDateTime.now());
            }
        }

        throw new ResourceNotFoundException("Fund price not found for code: " + code);
    }

    private RasyonetFundDto findRasyonetFundByCode(String fundCode) {
        String u = fundCode.toUpperCase();
        for (RasyonetFundDto f : rasyonetFundService.getAllFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllBesFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllOksFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        return null;
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
