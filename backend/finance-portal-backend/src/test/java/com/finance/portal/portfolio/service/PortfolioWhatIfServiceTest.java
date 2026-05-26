package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PortfolioWhatIfServiceTest {

    private InflationDeflatorService deflator;
    private PortfolioHistoricalPricePort pricePort;
    private EconomyDataPort economyDataPort;
    private PortfolioCurrencyConverter currencyConverter;
    private PortfolioWhatIfService service;

    @BeforeEach
    void setUp() {
        deflator = mock(InflationDeflatorService.class);
        pricePort = mock(PortfolioHistoricalPricePort.class);
        economyDataPort = mock(EconomyDataPort.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);
        // Default: tüm fiyat serileri boş Optional içinde boş NavigableMap
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));
        // currencyConverter — TRY için identity, USD için kur uygula (default 30)
        when(currencyConverter.toTry(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> {
                    BigDecimal amount = inv.getArgument(0);
                    String curr = inv.getArgument(1);
                    if (amount == null) return null;
                    if (curr == null || "TRY".equalsIgnoreCase(curr) || "TL".equalsIgnoreCase(curr)) {
                        return amount;
                    }
                    return amount.multiply(new BigDecimal("30"));  // sahte USD/TRY
                });

        service = new PortfolioWhatIfService(deflator, pricePort, economyDataPort, currencyConverter);
    }

    // ============================================================================
    // compute()
    // ============================================================================

    @Test
    @DisplayName("compute: null portföy → boş result, asOf bugün, scenarios boş")
    void compute_nullResp_returnsEmptyResult() {
        PortfolioWhatIfResult r = service.compute(null);

        assertThat(r).isNotNull();
        assertThat(r.getAsOf()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")));
        assertThat(r.getPortfolioId()).isNull();
        assertThat(r.getIncludedHoldings()).isZero();
        assertThat(r.getSkippedHoldings()).isZero();
        assertThat(r.getScenarios()).isEmpty();
    }

    @Test
    @DisplayName("compute: holdings null → boş result")
    void compute_nullHoldings_returnsEmptyResult() {
        PortfolioResponse resp = new PortfolioResponse();
        resp.setId(UUID.randomUUID());
        resp.setHoldings(null);

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(r.getPortfolioId()).isEqualTo(resp.getId());
        assertThat(r.getIncludedHoldings()).isZero();
        assertThat(r.getScenarios()).isEmpty();
    }

    @Test
    @DisplayName("compute: boş holdings → totalCost=0, scenarios boş")
    void compute_emptyHoldings_emptyScenarios() {
        PortfolioResponse resp = new PortfolioResponse();
        resp.setId(UUID.randomUUID());
        resp.setHoldings(List.of());

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(r.getIncludedHoldings()).isZero();
        assertThat(r.getTotalCost()).isEqualByComparingTo("0");
        assertThat(r.getActualValue()).isEqualByComparingTo("0");
        assertThat(r.getScenarios()).isEmpty();
    }

    @Test
    @DisplayName("compute: firstBuyDate yok / cost yok → skipped count artar, includedHoldings 0")
    void compute_holdingsWithMissingData_skipped() {
        PortfolioHoldingResponse missingDate = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1100"), "TRY", null);
        PortfolioHoldingResponse missingCost = holding("XAU", AssetType.GOLD,
                null, new BigDecimal("500"), "TRY", LocalDate.of(2026, 1, 1).atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(missingDate, missingCost));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(r.getIncludedHoldings()).isZero();
        assertThat(r.getSkippedHoldings()).isEqualTo(2);
        assertThat(r.getScenarios()).isEmpty();
    }

    @Test
    @DisplayName("compute: 1 geçerli holding (TL) → 4 senaryo dönder (inflation/gold/usd/deposit)")
    void compute_validHolding_returnsFourScenarios() {
        PortfolioHoldingResponse h = holding("THYAO", AssetType.STOCK,
                new BigDecimal("1000"), new BigDecimal("1500"), "TRY",
                LocalDate.of(2026, 1, 1).atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        PortfolioWhatIfResult r = service.compute(resp);

        assertThat(r.getIncludedHoldings()).isEqualTo(1);
        assertThat(r.getSkippedHoldings()).isZero();
        assertThat(r.getTotalCost()).isEqualByComparingTo("1000.00");
        assertThat(r.getActualValue()).isEqualByComparingTo("1500.00");
        // actualReturnPercent = (1500-1000)/1000 * 100 = 50%
        assertThat(r.getActualReturnPercent()).isEqualByComparingTo("50.00");
        // 4 senaryo: enflasyon / altın / dolar / mevduat
        assertThat(r.getScenarios()).hasSize(4);
        assertThat(r.getScenarios()).extracting("key")
                .containsExactlyInAnyOrder("inflation", "gold", "usd", "deposit");
    }

    @Test
    @DisplayName("compute: USD'li holding → currencyConverter.toTry çağrılır")
    void compute_usdHolding_currencyConverterUsed() {
        PortfolioHoldingResponse h = holding("AAPL", AssetType.STOCK,
                new BigDecimal("100"), new BigDecimal("150"), "USD",
                LocalDate.of(2026, 3, 1).atStartOfDay());
        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        PortfolioWhatIfResult r = service.compute(resp);

        // 100 USD × 30 = 3000 TL cost, 150 USD × 30 = 4500 TL value
        assertThat(r.getTotalCost()).isEqualByComparingTo("3000.00");
        assertThat(r.getActualValue()).isEqualByComparingTo("4500.00");
        // currencyConverter çağrıldı (her holding için cost + mv = 2 kez)
        verify(currencyConverter, atLeast(2)).toTry(any(), eq("USD"));
    }

    // ============================================================================
    // computeSimSeries() — validation
    // ============================================================================

    @Test
    @DisplayName("computeSimSeries: null asset_type → boş sonuç")
    void computeSimSeries_nullAssetType_returnsEmpty() {
        WhatIfSeriesResult r = service.computeSimSeries(null, "BTC",
                new BigDecimal("1000"), LocalDate.of(2026, 1, 1), List.of());

        assertThat(r.getPoints()).isEmpty();
        assertThat(r.getScope()).isEqualTo("SIM");
    }

    @Test
    @DisplayName("computeSimSeries: blank symbol → boş sonuç")
    void computeSimSeries_blankSymbol_returnsEmpty() {
        WhatIfSeriesResult r = service.computeSimSeries("CRYPTO", "  ",
                new BigDecimal("1000"), LocalDate.of(2026, 1, 1), List.of());

        assertThat(r.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("computeSimSeries: amount null → boş sonuç")
    void computeSimSeries_nullAmount_returnsEmpty() {
        WhatIfSeriesResult r = service.computeSimSeries("CRYPTO", "BTC",
                null, LocalDate.of(2026, 1, 1), List.of());

        assertThat(r.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("computeSimSeries: amount sıfır/negatif → boş sonuç")
    void computeSimSeries_nonPositiveAmount_returnsEmpty() {
        assertThat(service.computeSimSeries("CRYPTO", "BTC",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 1), List.of()).getPoints()).isEmpty();
        assertThat(service.computeSimSeries("CRYPTO", "BTC",
                new BigDecimal("-100"), LocalDate.of(2026, 1, 1), List.of()).getPoints()).isEmpty();
    }

    @Test
    @DisplayName("computeSimSeries: null tarih → boş sonuç")
    void computeSimSeries_nullDate_returnsEmpty() {
        WhatIfSeriesResult r = service.computeSimSeries("CRYPTO", "BTC",
                new BigDecimal("1000"), null, List.of());

        assertThat(r.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("computeSimSeries: gelecek tarih → boş sonuç")
    void computeSimSeries_futureDate_returnsEmpty() {
        LocalDate future = LocalDate.now().plusYears(1);
        WhatIfSeriesResult r = service.computeSimSeries("CRYPTO", "BTC",
                new BigDecimal("1000"), future, List.of());

        assertThat(r.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("computeSimSeries: geçersiz asset_type string → boş sonuç")
    void computeSimSeries_invalidAssetType_returnsEmpty() {
        WhatIfSeriesResult r = service.computeSimSeries("GARBAGE", "BTC",
                new BigDecimal("1000"), LocalDate.of(2026, 1, 1), List.of());

        assertThat(r.getPoints()).isEmpty();
    }

    // ============================================================================
    // helpers
    // ============================================================================

    private static PortfolioHoldingResponse holding(String symbol, AssetType type,
                                                    BigDecimal cost, BigDecimal mv,
                                                    String currency, LocalDateTime firstBuyDate) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setAssetType(type);
        h.setTotalCost(cost);
        h.setMarketValue(mv);
        h.setCurrency(currency);
        h.setFirstBuyDate(firstBuyDate);
        return h;
    }
}
