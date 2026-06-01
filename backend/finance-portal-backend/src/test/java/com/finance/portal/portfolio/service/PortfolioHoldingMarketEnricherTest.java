package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.enrich.BondHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CommodityHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CryptoHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FundHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FutureHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FxHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.GoldHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.StockHoldingEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PortfolioHoldingMarketEnricher} dispatcher davranışı: asset-type başına doğru
 * alt-enricher'a yönlendirme, bilinmeyen tip için generic price-snapshot fallback,
 * {@code UnsupportedOperationException}'in sessizce yutulması ve diğer hataların
 * degraded integration-log emit'i ile fail-soft ele alınması.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioHoldingMarketEnricherTest {

    @Mock AssetPriceQueryService assetPriceQueryService;
    @Mock CentralIntegrationLogService integrationLogService;
    @Mock CryptoHoldingEnricher cryptoHoldingEnricher;
    @Mock BondHoldingEnricher bondHoldingEnricher;
    @Mock StockHoldingEnricher stockHoldingEnricher;
    @Mock FutureHoldingEnricher futureHoldingEnricher;
    @Mock FxHoldingEnricher fxHoldingEnricher;
    @Mock GoldHoldingEnricher goldHoldingEnricher;
    @Mock CommodityHoldingEnricher commodityHoldingEnricher;
    @Mock FundHoldingEnricher fundHoldingEnricher;

    private PortfolioHoldingMarketEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new PortfolioHoldingMarketEnricher(
                assetPriceQueryService, integrationLogService,
                cryptoHoldingEnricher, bondHoldingEnricher, stockHoldingEnricher,
                futureHoldingEnricher, fxHoldingEnricher, goldHoldingEnricher,
                commodityHoldingEnricher, fundHoldingEnricher);
    }

    private static PortfolioHoldingResponse holding(AssetType type, String symbol) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setAssetType(type);
        h.setSymbol(symbol);
        return h;
    }

    // ---- per-type dispatch -------------------------------------------------

    @Test
    @DisplayName("STOCK → stockHoldingEnricher (yalnız o çağrılır)")
    void dispatch_stock() {
        PortfolioHoldingResponse h = holding(AssetType.STOCK, "THYAO");
        enricher.enrich(h);
        verify(stockHoldingEnricher).enrich(h);
        verifyNoInteractions(goldHoldingEnricher, commodityHoldingEnricher,
                cryptoHoldingEnricher, bondHoldingEnricher, fundHoldingEnricher,
                fxHoldingEnricher, futureHoldingEnricher, assetPriceQueryService);
    }

    @Test
    @DisplayName("FUTURE → futureHoldingEnricher")
    void dispatch_future() {
        PortfolioHoldingResponse h = holding(AssetType.FUTURE, "F_XU0300625");
        enricher.enrich(h);
        verify(futureHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("CRYPTO → cryptoHoldingEnricher")
    void dispatch_crypto() {
        PortfolioHoldingResponse h = holding(AssetType.CRYPTO, "BTC");
        enricher.enrich(h);
        verify(cryptoHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("GOLD → goldHoldingEnricher")
    void dispatch_gold() {
        PortfolioHoldingResponse h = holding(AssetType.GOLD, "GRAM");
        enricher.enrich(h);
        verify(goldHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("COMMODITY → commodityHoldingEnricher")
    void dispatch_commodity() {
        PortfolioHoldingResponse h = holding(AssetType.COMMODITY, "NG=F");
        enricher.enrich(h);
        verify(commodityHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("FUND → fundHoldingEnricher")
    void dispatch_fund() {
        PortfolioHoldingResponse h = holding(AssetType.FUND, "AFA");
        enricher.enrich(h);
        verify(fundHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("BOND → bondHoldingEnricher")
    void dispatch_bond() {
        PortfolioHoldingResponse h = holding(AssetType.BOND, "TRT...");
        enricher.enrich(h);
        verify(bondHoldingEnricher).enrich(h);
    }

    @Test
    @DisplayName("FX → fxHoldingEnricher")
    void dispatch_fx() {
        PortfolioHoldingResponse h = holding(AssetType.FX, "USD");
        enricher.enrich(h);
        verify(fxHoldingEnricher).enrich(h);
    }

    // ---- unknown / null type → price snapshot fallback ---------------------

    @Test
    @DisplayName("null assetType → generic AssetPriceQueryService snapshot fallback")
    void dispatch_nullType_usesSnapshot() {
        LocalDateTime asOf = LocalDateTime.of(2026, 5, 26, 16, 0);
        AssetPriceSnapshot snap = new AssetPriceSnapshot(
                null, "XYZ", new BigDecimal("123.45"), "USD", asOf);
        when(assetPriceQueryService.getCurrentPrice(isNull(), eq("XYZ"))).thenReturn(snap);

        PortfolioHoldingResponse h = holding(null, "XYZ");
        h.setTotalQuantity(new BigDecimal("2"));
        h.setTotalCost(new BigDecimal("200"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("123.45");
        assertThat(h.getMarketValue()).isEqualByComparingTo("246.9000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("46.9000");
        assertThat(h.getCurrency()).isEqualTo("USD");
        assertThat(h.getAsOf()).isEqualTo(asOf);
        verifyNoInteractions(stockHoldingEnricher, goldHoldingEnricher);
    }

    // ---- error handling ----------------------------------------------------

    @Test
    @DisplayName("UnsupportedOperationException → sessizce yutulur (degraded log YOK)")
    void unsupportedOperation_swallowedNoLog() {
        PortfolioHoldingResponse h = holding(AssetType.GOLD, "BOGUS");
        doThrow(new UnsupportedOperationException("no live price"))
                .when(goldHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(integrationLogService, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("Genel Exception → degraded MARKET_DATA_FETCH_FAILED log emit edilir (provider=yahoo)")
    void genericException_publishesDegraded() {
        PortfolioHoldingResponse h = holding(AssetType.COMMODITY, "NG=F");
        doThrow(new RuntimeException("yahoo timeout"))
                .when(commodityHoldingEnricher).enrich(h);

        enricher.enrich(h);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"),
                eq("WARN"),
                anyString(),
                eq("yahoo"),            // COMMODITY → PROVIDER_YAHOO
                eq("portfolio_enrich"),
                isNull(), isNull(), isNull(),
                eq(true),
                metaCaptor.capture(),
                anyString());

        Map<String, Object> meta = metaCaptor.getValue();
        assertThat(meta).containsEntry("assetType", "COMMODITY");
        assertThat(meta).containsEntry("symbol", "NG=F");
        assertThat(meta).containsEntry("degraded", true);
    }

    @Test
    @DisplayName("Genel Exception (CRYPTO) → provider=coingecko")
    void genericException_cryptoProvider() {
        PortfolioHoldingResponse h = holding(AssetType.CRYPTO, "BTC");
        doThrow(new IllegalStateException("boom")).when(cryptoHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"), eq("WARN"), anyString(),
                eq("coingecko"), eq("portfolio_enrich"),
                isNull(), isNull(), isNull(), eq(true), any(), anyString());
    }

    @Test
    @DisplayName("Genel Exception (FUND) → provider=rasyonet")
    void genericException_fundProvider() {
        PortfolioHoldingResponse h = holding(AssetType.FUND, "AFA");
        doThrow(new RuntimeException("rasyonet down")).when(fundHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"), eq("WARN"), anyString(),
                eq("rasyonet"), eq("portfolio_enrich"),
                isNull(), isNull(), isNull(), eq(true), any(), anyString());
    }

    @Test
    @DisplayName("Genel Exception (FUTURE) → provider=akbank_viop")
    void genericException_futureProvider() {
        PortfolioHoldingResponse h = holding(AssetType.FUTURE, "F_XU030");
        doThrow(new RuntimeException("viop down")).when(futureHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"), eq("WARN"), anyString(),
                eq("akbank_viop"), eq("portfolio_enrich"),
                isNull(), isNull(), isNull(), eq(true), any(), anyString());
    }

    @Test
    @DisplayName("Genel Exception (BOND, mapping yok) → provider=external")
    void genericException_defaultProvider() {
        PortfolioHoldingResponse h = holding(AssetType.BOND, "TRT");
        doThrow(new RuntimeException("bond down")).when(bondHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"), eq("WARN"), anyString(),
                eq("external"), eq("portfolio_enrich"),
                isNull(), isNull(), isNull(), eq(true), any(), anyString());
    }

    @Test
    @DisplayName("Snapshot fallback'ta hata → degraded log (assetType=UNKNOWN, provider=external)")
    void snapshotException_publishesDegradedUnknown() {
        PortfolioHoldingResponse h = holding(null, "ZZZ");
        when(assetPriceQueryService.getCurrentPrice(isNull(), eq("ZZZ")))
                .thenThrow(new RuntimeException("not found"));

        enricher.enrich(h);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(integrationLogService).publish(
                eq("MARKET_DATA_FETCH_FAILED"), eq("WARN"), anyString(),
                eq("external"), eq("portfolio_enrich"),
                isNull(), isNull(), isNull(), eq(true),
                metaCaptor.capture(), anyString());
        assertThat(metaCaptor.getValue()).containsEntry("assetType", "UNKNOWN");
    }

    @Test
    @DisplayName("Happy path: enricher başarılı → hiç log emit edilmez")
    void happyPath_noLog() {
        PortfolioHoldingResponse h = holding(AssetType.STOCK, "THYAO");
        doNothing().when(stockHoldingEnricher).enrich(h);

        enricher.enrich(h);

        verify(stockHoldingEnricher, times(1)).enrich(h);
        verifyNoInteractions(integrationLogService);
    }
}
