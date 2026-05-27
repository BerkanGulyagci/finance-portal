package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.enrich.BondHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CommodityHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CryptoHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FundHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FutureHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FxHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.GoldHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.StockHoldingEnricher;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * Portföy pozisyonları için canlı piyasa alanlarını doldurur. Asset-type başına ayrı
 * {@code *HoldingEnricher} bileşenleri vardır (paket {@code service/enrich}); bu sınıf
 * sadece dispatcher + bilinmeyen tipler için generic price snapshot fallback'ı +
 * tüm enrich akışı için ortak hata logu yayımlama görevini üstlenir.
 */
@Component
public class PortfolioHoldingMarketEnricher implements HoldingMarketEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHoldingMarketEnricher.class);

    private final AssetPriceQueryService assetPriceQueryService;
    private final CentralIntegrationLogService integrationLogService;
    private final CryptoHoldingEnricher cryptoHoldingEnricher;
    private final BondHoldingEnricher bondHoldingEnricher;
    private final StockHoldingEnricher stockHoldingEnricher;
    private final FutureHoldingEnricher futureHoldingEnricher;
    private final FxHoldingEnricher fxHoldingEnricher;
    private final GoldHoldingEnricher goldHoldingEnricher;
    private final CommodityHoldingEnricher commodityHoldingEnricher;
    private final FundHoldingEnricher fundHoldingEnricher;

    public PortfolioHoldingMarketEnricher(AssetPriceQueryService assetPriceQueryService,
                                          CentralIntegrationLogService integrationLogService,
                                          CryptoHoldingEnricher cryptoHoldingEnricher,
                                          BondHoldingEnricher bondHoldingEnricher,
                                          StockHoldingEnricher stockHoldingEnricher,
                                          FutureHoldingEnricher futureHoldingEnricher,
                                          FxHoldingEnricher fxHoldingEnricher,
                                          GoldHoldingEnricher goldHoldingEnricher,
                                          CommodityHoldingEnricher commodityHoldingEnricher,
                                          FundHoldingEnricher fundHoldingEnricher) {
        this.assetPriceQueryService = assetPriceQueryService;
        this.integrationLogService = integrationLogService;
        this.cryptoHoldingEnricher = cryptoHoldingEnricher;
        this.bondHoldingEnricher = bondHoldingEnricher;
        this.stockHoldingEnricher = stockHoldingEnricher;
        this.futureHoldingEnricher = futureHoldingEnricher;
        this.fxHoldingEnricher = fxHoldingEnricher;
        this.goldHoldingEnricher = goldHoldingEnricher;
        this.commodityHoldingEnricher = commodityHoldingEnricher;
        this.fundHoldingEnricher = fundHoldingEnricher;
    }

    @Override
    @WithSpan("PortfolioEnrich.holding")
    public void enrich(PortfolioHoldingResponse holding) {
        try {
            AssetType type = holding.getAssetType();
            Span.current().setAttribute("holding.asset_type", type != null ? type.name() : "null");
            Span.current().setAttribute("holding.symbol", String.valueOf(holding.getSymbol()));
            if (type == AssetType.STOCK) {
                stockHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FUTURE) {
                futureHoldingEnricher.enrich(holding);
            } else if (type == AssetType.CRYPTO) {
                cryptoHoldingEnricher.enrich(holding);
            } else if (type == AssetType.GOLD) {
                goldHoldingEnricher.enrich(holding);
            } else if (type == AssetType.COMMODITY) {
                commodityHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FUND) {
                fundHoldingEnricher.enrich(holding);
            } else if (type == AssetType.BOND) {
                bondHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FX) {
                fxHoldingEnricher.enrich(holding);
            } else {
                // Bilinmeyen / fallback — generic price snapshot
                enrichFromPriceSnapshot(holding);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Live price not supported for assetType={} symbol={}",
                    holding.getAssetType(), holding.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to fetch live price for assetType={} symbol={}: {}",
                    holding.getAssetType(), holding.getSymbol(), ex.getMessage());
            publishDegradedMarketFetch(holding.getAssetType(), holding.getSymbol());
        }
    }

    private void publishDegradedMarketFetch(AssetType assetType, String symbol) {
        String provider = resolveProviderForAssetType(assetType);
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_MARKET_DATA_FETCH_FAILED,
                "WARN",
                "Portfolio holding live price enrichment failed",
                provider,
                "portfolio_enrich",
                null,
                null,
                null,
                true,
                Map.of(
                        "assetType", assetType != null ? assetType.name() : "UNKNOWN",
                        "symbol", symbol != null ? symbol : "",
                        "degraded", true,
                        "trigger", IntegrationLogSupport.TRIGGER_API_REQUEST
                ),
                PortfolioHoldingMarketEnricher.class.getName()
        );
    }

    private static String resolveProviderForAssetType(AssetType assetType) {
        if (assetType == null) {
            return IntegrationLogSupport.PROVIDER_EXTERNAL;
        }
        return switch (assetType) {
            case STOCK, COMMODITY -> IntegrationLogSupport.PROVIDER_YAHOO;
            case CRYPTO -> IntegrationLogSupport.PROVIDER_COINGECKO;
            case FUND -> IntegrationLogSupport.PROVIDER_RASYONET;
            case FUTURE -> IntegrationLogSupport.PROVIDER_AKBANK_VIOP;
            default -> IntegrationLogSupport.PROVIDER_EXTERNAL;
        };
    }

    /** Bilinmeyen tipler için fallback: AssetPriceQueryService anlık fiyat snapshot'ı. */
    private void enrichFromPriceSnapshot(PortfolioHoldingResponse holding) {
        AssetPriceSnapshot snapshot = assetPriceQueryService.getCurrentPrice(
                holding.getAssetType(), holding.getSymbol());

        BigDecimal currentPrice = snapshot.getPrice();
        BigDecimal mv = marketValue(currentPrice, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        holding.setCurrentPrice(currentPrice);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(snapshot.getCurrency());
        holding.setAsOf(snapshot.getAsOf());
    }
}
