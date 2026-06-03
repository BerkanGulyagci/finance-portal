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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Supplementary branch-coverage tests for {@link DefaultAssetPriceQueryService}.
 *
 * Covers branches NOT exercised by {@code DefaultAssetPriceQueryServiceTest}:
 *  - parseDateTime catch arm (unparseable asOf) for STOCK / FUTURE
 *  - parseLastUpdated null arm + catch arm (unparseable crypto lastUpdated)
 *  - findRasyonetFundByCode: BES-list match, OKS-list match, null-code skip arm
 *  - fetchFundPrice: listed-but-zero/null price falls through to detail loop
 *  - fetchFundPrice: detail currencyCode null/blank -> "TRY" fallback
 *  - fetchFundPrice: detail loop skips first source (null / zero price) then succeeds on a later one
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultAssetPriceQueryServiceMoreTest {

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

    private StockSummary mkStock(String symbol, BigDecimal price, String currency, String asOf) {
        StockSummary s = new StockSummary();
        s.setSymbol(symbol);
        s.setPrice(price);
        s.setCurrency(currency);
        s.setAsOf(asOf);
        return s;
    }

    private CryptoMarketItem mkCrypto(String symbol, BigDecimal price, String lastUpdated) {
        return new CryptoMarketItem(
                "id-" + symbol, symbol, "name", "img",
                price, null, null, null, null, null,
                null, null, null, null, lastUpdated);
    }

    private RasyonetFundDto mkFund(String code, BigDecimal price) {
        RasyonetFundDto d = new RasyonetFundDto();
        d.setCode(code);
        d.setPrice(price);
        return d;
    }

    private RasyonetFundDetailDto mkDetail(BigDecimal price, String currencyCode) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setPrice(price);
        d.setCurrencyCode(currencyCode);
        return d;
    }

    // ── parseDateTime catch arm (STOCK) ──────────────────────────────────────────

    @Test
    void stock_unparseableAsOf_fallsBackToNow() {
        // asOf is not an ISO LocalDateTime -> parse throws -> catch returns now()
        when(stockQueryService.getStockSummary("AAPL"))
                .thenReturn(mkStock("AAPL", new BigDecimal("190.50"), "USD", "garbage-not-a-date"));

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.STOCK, "aapl");
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        assertThat(snap.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(snap.getAsOf()).isAfter(before).isBefore(after);
    }

    // ── parseDateTime catch arm (FUTURE) ─────────────────────────────────────────

    @Test
    void future_unparseableAsOf_fallsBackToNow() {
        when(stockQueryService.getStockSummary("ES=F"))
                .thenReturn(mkStock("ES=F", new BigDecimal("5000.00"), "USD", "11:22"));

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUTURE, "es=f");
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        assertThat(snap.getAssetType()).isEqualTo(AssetType.FUTURE);
        assertThat(snap.getAsOf()).isAfter(before).isBefore(after);
    }

    // ── parseDateTime blank arm (FUTURE) ─────────────────────────────────────────

    @Test
    void stock_blankAsOf_fallsBackToNow() {
        when(stockQueryService.getStockSummary("MSFT"))
                .thenReturn(mkStock("MSFT", new BigDecimal("400.00"), "USD", "   "));

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.STOCK, "msft");
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        assertThat(snap.getAsOf()).isAfter(before).isBefore(after);
    }

    // ── parseLastUpdated null arm (CRYPTO) ───────────────────────────────────────

    @Test
    void crypto_nullLastUpdated_usesNow() {
        when(cryptoMarketService.findBySymbol("eth"))
                .thenReturn(mkCrypto("eth", new BigDecimal("123456"), null));

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.CRYPTO, "eth");
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        assertThat(snap.getSymbol()).isEqualTo("ETH");
        assertThat(snap.getPrice()).isEqualByComparingTo("123456");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        assertThat(snap.getAsOf()).isAfter(before).isBefore(after);
    }

    // ── parseLastUpdated catch arm (CRYPTO) ──────────────────────────────────────

    @Test
    void crypto_unparseableLastUpdated_warnsAndUsesNow() {
        // not an OffsetDateTime -> parse throws -> warn + now()
        when(cryptoMarketService.findBySymbol("xrp"))
                .thenReturn(mkCrypto("xrp", new BigDecimal("42.50"), "2026/01/02 10:15"));

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.CRYPTO, "xrp");
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        assertThat(snap.getSymbol()).isEqualTo("XRP");
        assertThat(snap.getAsOf()).isAfter(before).isBefore(after);
    }

    // ── findRasyonetFundByCode: match in BES list ────────────────────────────────

    @Test
    void fund_foundInBesFunds() {
        // not in main list, found in BES list
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds())
                .thenReturn(List.of(mkFund("BES1", new BigDecimal("5.55"))));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "bes1");

        assertThat(snap.getAssetType()).isEqualTo(AssetType.FUND);
        assertThat(snap.getSymbol()).isEqualTo("BES1");
        assertThat(snap.getPrice()).isEqualByComparingTo("5.55");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        verify(rasyonetFundService).getAllBesFunds();
    }

    // ── findRasyonetFundByCode: match in OKS list (+ null-code skip arm) ──────────

    @Test
    void fund_foundInOksFunds_skipsNullCodeEntries() {
        // main + BES empty; OKS list has a null-code entry (skipped) then the match
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        // mkFund(null, ...) exercises the `f.getCode() != null` false arm
        when(rasyonetFundService.getAllOksFunds())
                .thenReturn(Arrays.asList(
                        mkFund(null, new BigDecimal("1.11")),
                        mkFund("OKS9", new BigDecimal("7.77"))));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "oks9");

        assertThat(snap.getSymbol()).isEqualTo("OKS9");
        assertThat(snap.getPrice()).isEqualByComparingTo("7.77");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
        verify(rasyonetFundService).getAllOksFunds();
    }

    // ── fetchFundPrice: listed found but price null -> falls through to detail ────

    @Test
    void fund_listedPriceNull_fallsThroughToDetail() {
        // listed entry exists but has a null price -> guard fails -> detail loop
        when(rasyonetFundService.getAllFunds())
                .thenReturn(List.of(mkFund("NUL", null)));
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        when(rasyonetFundService.getFundDetailRich(eq("NUL"), eq("TMF")))
                .thenReturn(mkDetail(new BigDecimal("3.21"), "EUR"));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "nul");

        assertThat(snap.getSymbol()).isEqualTo("NUL");
        assertThat(snap.getPrice()).isEqualByComparingTo("3.21");
        assertThat(snap.getCurrency()).isEqualTo("EUR");
        verify(rasyonetFundService).getFundDetailRich("NUL", "TMF");
    }

    // ── fetchFundPrice: listed found but price zero -> falls through to detail ────

    @Test
    void fund_listedPriceZero_fallsThroughToDetail_currencyBlankDefaultsTry() {
        // listed price == 0 -> compareTo(ZERO) > 0 is false -> detail loop.
        // detail currencyCode is blank -> currency defaults to "TRY".
        when(rasyonetFundService.getAllFunds())
                .thenReturn(List.of(mkFund("ZER", BigDecimal.ZERO)));
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        when(rasyonetFundService.getFundDetailRich(eq("ZER"), eq("TMF")))
                .thenReturn(mkDetail(new BigDecimal("8.80"), "   "));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "zer");

        assertThat(snap.getSymbol()).isEqualTo("ZER");
        assertThat(snap.getPrice()).isEqualByComparingTo("8.80");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
    }

    // ── fetchFundPrice: detail null currencyCode -> "TRY" fallback ───────────────

    @Test
    void fund_detailNullCurrency_defaultsTry() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        when(rasyonetFundService.getFundDetailRich(eq("NCUR"), eq("TMF")))
                .thenReturn(mkDetail(new BigDecimal("2.50"), null));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "ncur");

        assertThat(snap.getSymbol()).isEqualTo("NCUR");
        assertThat(snap.getPrice()).isEqualByComparingTo("2.50");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
    }

    // ── fetchFundPrice: detail loop skips null/zero source, succeeds on later one ─

    @Test
    void fund_detailLoop_skipsNullAndZero_thenSucceedsOnThirdSource() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        // TMF -> null (skip), TPF -> zero price (skip via compareTo), TAF -> valid
        when(rasyonetFundService.getFundDetailRich(eq("LATE"), eq("TMF"))).thenReturn(null);
        when(rasyonetFundService.getFundDetailRich(eq("LATE"), eq("TPF")))
                .thenReturn(mkDetail(BigDecimal.ZERO, "USD"));
        when(rasyonetFundService.getFundDetailRich(eq("LATE"), eq("TAF")))
                .thenReturn(mkDetail(new BigDecimal("4.44"), "USD"));

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FUND, "late");

        assertThat(snap.getSymbol()).isEqualTo("LATE");
        assertThat(snap.getPrice()).isEqualByComparingTo("4.44");
        assertThat(snap.getCurrency()).isEqualTo("USD");
        verify(rasyonetFundService).getFundDetailRich("LATE", "TMF");
        verify(rasyonetFundService).getFundDetailRich("LATE", "TPF");
        verify(rasyonetFundService).getFundDetailRich("LATE", "TAF");
    }

    // ── fetchFundPrice: detail has null price -> skip arm, then not found ─────────

    @Test
    void fund_detailNullPrice_allSourcesSkipped_throwsNotFound() {
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());

        // every detail has a null price -> `d.getPrice() != null` false arm -> loop ends -> throw
        when(rasyonetFundService.getFundDetailRich(eq("NPX"), eq("TMF")))
                .thenReturn(mkDetail(null, "USD"));
        when(rasyonetFundService.getFundDetailRich(eq("NPX"), eq("TPF")))
                .thenReturn(mkDetail(null, "USD"));
        when(rasyonetFundService.getFundDetailRich(eq("NPX"), eq("TAF")))
                .thenReturn(mkDetail(null, "USD"));

        assertThatThrownBy(() -> service.getCurrentPrice(AssetType.FUND, "npx"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NPX");
    }

    // ── FX: unit exactly 1 boundary already covered elsewhere; here unit < 1 path ─
    // (unit field is int; values <= 1 take the no-normalize arm). Validate trim/upper too.

    @Test
    void fx_symbolFound_unitOne_noNormalization() {
        FxRateItem eur = new FxRateItem("EUR", new BigDecimal("35.00"), new BigDecimal("35.40"), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-01-02", List.of(eur));
        when(marketFxService.getTcmbLatestRates(null)).thenReturn(rates);

        AssetPriceSnapshot snap = service.getCurrentPrice(AssetType.FX, "eur");

        assertThat(snap.getSymbol()).isEqualTo("EUR");
        assertThat(snap.getPrice()).isEqualByComparingTo("35.40");
        assertThat(snap.getCurrency()).isEqualTo("TRY");
    }
}
