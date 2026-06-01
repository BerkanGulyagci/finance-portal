package com.finance.portal.market.application;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAssetPriceQueryServiceTest {

    @Mock
    private StockQueryService stockQueryService;
    @Mock
    private CryptoMarketService cryptoMarketService;
    @Mock
    private MarketFxService marketFxService;
    @Mock
    private RasyonetFundService rasyonetFundService;

    @InjectMocks
    private DefaultAssetPriceQueryService service;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private StockSummary stockSummary(String symbol, BigDecimal price, String currency, String asOf) {
        StockSummary s = new StockSummary();
        s.setSymbol(symbol);
        s.setPrice(price);
        s.setCurrency(currency);
        s.setAsOf(asOf);
        return s;
    }

    private CryptoMarketItem cryptoItem(String symbol, BigDecimal price, String lastUpdated) {
        return new CryptoMarketItem(
                "id-" + symbol, symbol, "name", "img",
                price, null, null, null, null, null,
                null, null, null, null, lastUpdated);
    }

    private RasyonetFundDto fundDto(String code, BigDecimal price) {
        RasyonetFundDto d = new RasyonetFundDto();
        d.setCode(code);
        d.setPrice(price);
        return d;
    }

    // ── Null / blank argument validation ───────────────────────────────────────

    @Test
    void nullAssetType_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCurrentPrice(null, "AAPL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assetType");
        verifyNoInteractions(stockQueryService, cryptoMarketService, marketFxService, rasyonetFundService);
    }

    @Test
    void nullSymbol_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.STOCK, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
        verifyNoInteractions(stockQueryService, cryptoMarketService, marketFxService, rasyonetFundService);
    }

    @Test
    void blankSymbol_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.STOCK, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
        verifyNoInteractions(stockQueryService, cryptoMarketService, marketFxService, rasyonetFundService);
    }

    // ── STOCK routing ──────────────────────────────────────────────────────────

    @Test
    void stock_routesToStockQueryService_upperCasesSymbol() {
        when(stockQueryService.getStockSummary("AAPL"))
                .thenReturn(stockSummary("AAPL", new BigDecimal("190.50"), "USD", "2026-01-02T10:15:30"));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.STOCK, "aapl");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(snap.getSymbol()).isEqualTo("AAPL");
        assertThat(snap.getPrice()).isEqualByComparingTo("190.50");
        assertThat(snap.getCurrency()).isEqualTo("USD");
        verify(stockQueryService).getStockSummary("AAPL");
        verifyNoInteractions(cryptoMarketService, marketFxService, rasyonetFundService);
    }

    // ── CRYPTO routing ─────────────────────────────────────────────────────────

    @Test
    void crypto_routesToCryptoMarketService() {
        when(cryptoMarketService.findBySymbol("btc"))
                .thenReturn(cryptoItem("btc", new BigDecimal("2500000"), "2026-01-02T10:15:30Z"));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.CRYPTO, "btc");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.CRYPTO);
        assertThat(snap.getSymbol()).isEqualTo("BTC");
        assertThat(snap.getPrice()).isEqualByComparingTo("2500000");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        verify(cryptoMarketService).findBySymbol("btc");
        verifyNoInteractions(stockQueryService, marketFxService, rasyonetFundService);
    }

    // ── FX routing ─────────────────────────────────────────────────────────────

    @Test
    void fx_routesToMarketFxService_unitOne() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("32.00"), new BigDecimal("32.50"), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-01-02", List.of(usd));
        when(marketFxService.getTcmbLatestRates(null)).thenReturn(rates);

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FX, "usd");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.FX);
        assertThat(snap.getSymbol()).isEqualTo("USD");
        assertThat(snap.getPrice()).isEqualByComparingTo("32.50");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        verify(marketFxService).getTcmbLatestRates(null);
        verifyNoInteractions(stockQueryService, cryptoMarketService, rasyonetFundService);
    }

    @Test
    void fx_normalizesPriceByUnit() {
        FxRateItem jpy = new FxRateItem("JPY", new BigDecimal("0.20"), new BigDecimal("22.00"), 100);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-01-02", List.of(jpy));
        when(marketFxService.getTcmbLatestRates(null)).thenReturn(rates);

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FX, "JPY");

        // 22.00 / 100 = 0.22 (HALF_UP, scale 6)
        assertThat(snap.getPrice()).isEqualByComparingTo("0.22");
    }

    @Test
    void fx_symbolNotFound_throwsResourceNotFound() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("32.00"), new BigDecimal("32.50"), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-01-02", List.of(usd));
        when(marketFxService.getTcmbLatestRates(null)).thenReturn(rates);

        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.FX, "EUR"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("EUR");
    }

    // ── FUND routing ───────────────────────────────────────────────────────────

    @Test
    void fund_foundInListedFunds_routesToRasyonet() {
        when(rasyonetFundService.getAllFunds())
                .thenReturn(List.of(fundDto("AFA", new BigDecimal("12.34"))));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "afa");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.FUND);
        assertThat(snap.getSymbol()).isEqualTo("AFA");
        assertThat(snap.getPrice()).isEqualByComparingTo("12.34");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        verify(rasyonetFundService).getAllFunds();
        verifyNoInteractions(stockQueryService, cryptoMarketService, marketFxService);
    }

    @Test
    void fund_notInLists_fallsBackToDetailRich() {
        // not in any list
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        RasyonetFundDetailDto detail = new RasyonetFundDetailDto();
        detail.setPrice(new BigDecimal("9.99"));
        detail.setCurrencyCode("USD");
        // service iterates TMF, TPF, TAF; first non-null positive price wins
        when(rasyonetFundService.getFundDetailRich(eq("ZZZ"), eq("TMF"))).thenReturn(detail);
        lenient().when(rasyonetFundService.getFundDetailRich(eq("ZZZ"), eq("TPF"))).thenReturn(null);
        lenient().when(rasyonetFundService.getFundDetailRich(eq("ZZZ"), eq("TAF"))).thenReturn(null);

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "zzz");

        assertThat(snap.getSymbol()).isEqualTo("ZZZ");
        assertThat(snap.getPrice()).isEqualByComparingTo("9.99");
        assertThat(snap.getCurrency()).isEqualTo("USD");
        verify(rasyonetFundService).getFundDetailRich("ZZZ", "TMF");
    }

    @Test
    void fund_notFoundAnywhere_throwsResourceNotFound() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
        when(rasyonetFundService.getFundDetailRich(eq("NOPE"), eq("TMF"))).thenReturn(null);
        when(rasyonetFundService.getFundDetailRich(eq("NOPE"), eq("TPF"))).thenReturn(null);
        when(rasyonetFundService.getFundDetailRich(eq("NOPE"), eq("TAF"))).thenReturn(null);

        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.FUND, "nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    // ── FUTURE routing ─────────────────────────────────────────────────────────

    @Test
    void future_routesToStockQueryService() {
        when(stockQueryService.getStockSummary("GC=F"))
                .thenReturn(stockSummary("GC=F", new BigDecimal("2050.00"), "USD", null));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUTURE, "gc=f");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.FUTURE);
        assertThat(snap.getSymbol()).isEqualTo("GC=F");
        assertThat(snap.getPrice()).isEqualByComparingTo("2050.00");
        verify(stockQueryService).getStockSummary("GC=F");
        verifyNoInteractions(cryptoMarketService, marketFxService, rasyonetFundService);
    }

    // ── Unsupported asset types ────────────────────────────────────────────────

    @Test
    void gold_isUnsupported() {
        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.GOLD, "XAU"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GOLD");
        verifyNoInteractions(stockQueryService, cryptoMarketService, marketFxService, rasyonetFundService);
    }

    @Test
    void commodity_isUnsupported() {
        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.COMMODITY, "WTI"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bond_isUnsupported() {
        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.BOND, "XS123"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
