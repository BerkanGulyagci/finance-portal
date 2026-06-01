package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.application.logging.CentralErrorLogService;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.presentation.exception.GlobalExceptionHandler;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.presentation.mapper.MarketFxPresentationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link MarketFxController}.
 * Kapsam: GET /tcmb/latest (200 + body shape), GET /open/latest (base/symbol normalizasyonu
 * + 200), GET /history (default range + 200) ve geçersiz base/symbol → 400 (validasyon
 * IllegalArgumentException). Validasyon hatasının 400'e maplenmesi için gerçek
 * {@link GlobalExceptionHandler} controller-advice olarak kaydedilir (logging servisleri mock).
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class MarketFxControllerTest {

    @Mock MarketFxService marketFxService;
    @Mock CentralErrorLogService centralErrorLogService;
    @Mock CentralIntegrationLogService centralIntegrationLogService;

    // Gerçek mapper kullanılır — model → response dönüşümünü de kapsar.
    private final MarketFxPresentationMapper fxMapper = new MarketFxPresentationMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MarketFxController(marketFxService, fxMapper))
                .setControllerAdvice(new GlobalExceptionHandler(centralErrorLogService, centralIntegrationLogService))
                .build();
    }

    private FxLatestRates tcmbRates() {
        FxRateItem usd = new FxRateItem("USD",
                new BigDecimal("32.10"), new BigDecimal("32.40"), 1,
                new BigDecimal("32.05"), new BigDecimal("32.45"));
        return new FxLatestRates("tcmb", "official", "TRY", "2026-05-30", List.of(usd));
    }

    private FxLatestRates openRates(String base) {
        FxRateItem eur = new FxRateItem("EUR", new BigDecimal("0.92"), new BigDecimal("0.92"), 1);
        return new FxLatestRates("openfx", "global", base, "2026-05-30T00:00:00Z", List.of(eur));
    }

    private FxHistory history(String symbol, String range) {
        return new FxHistory(symbol, "TRY", range,
                List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("31.80"))));
    }

    @Test
    void getTcmbLatestRates_returns200_withRatesShape() throws Exception {
        when(marketFxService.getTcmbLatestRates(null)).thenReturn(tcmbRates());

        mockMvc.perform(get("/api/market/fx/tcmb/latest").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("TCMB FX rates retrieved successfully"))
                .andExpect(jsonPath("$.data.provider").value("tcmb"))
                .andExpect(jsonPath("$.data.base").value("TRY"))
                .andExpect(jsonPath("$.data.asOf").value("2026-05-30"))
                .andExpect(jsonPath("$.data.rates.length()").value(1))
                .andExpect(jsonPath("$.data.rates[0].symbol").value("USD"))
                .andExpect(jsonPath("$.data.rates[0].buy").value(32.10))
                .andExpect(jsonPath("$.data.rates[0].effectiveSell").value(32.45));
    }

    @Test
    void getTcmbLatestRates_passesSymbolsThrough() throws Exception {
        when(marketFxService.getTcmbLatestRates("USD,EUR")).thenReturn(tcmbRates());

        mockMvc.perform(get("/api/market/fx/tcmb/latest").param("symbols", "USD,EUR").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("tcmb"));

        verify(marketFxService).getTcmbLatestRates("USD,EUR");
    }

    @Test
    void getOpenFxLatest_noParams_passesNulls() throws Exception {
        when(marketFxService.getOpenFxLatest(null, null)).thenReturn(openRates("USD"));

        mockMvc.perform(get("/api/market/fx/open/latest").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Open FX rates retrieved successfully"))
                .andExpect(jsonPath("$.data.provider").value("openfx"))
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.rates[0].symbol").value("EUR"));

        verify(marketFxService).getOpenFxLatest(null, null);
    }

    @Test
    void getOpenFxLatest_normalizesBaseAndSymbolsToUpper() throws Exception {
        // base "usd" -> "USD"; symbols "eur, gbp" -> "EUR,GBP"
        when(marketFxService.getOpenFxLatest("USD", "EUR,GBP")).thenReturn(openRates("USD"));

        mockMvc.perform(get("/api/market/fx/open/latest")
                        .param("base", "usd")
                        .param("symbols", "eur, gbp")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("openfx"));

        verify(marketFxService).getOpenFxLatest("USD", "EUR,GBP");
    }

    @Test
    void getOpenFxLatest_invalidBase_returns400() throws Exception {
        mockMvc.perform(get("/api/market/fx/open/latest").param("base", "DOLLAR").accept("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Base currency must be a 3-letter uppercase code, e.g. USD"));

        // Validasyon controller'da; servis çağrılmamalı.
        verify(marketFxService, never()).getOpenFxLatest(isNull(), isNull());
    }

    @Test
    void getOpenFxLatest_invalidSymbol_returns400() throws Exception {
        mockMvc.perform(get("/api/market/fx/open/latest").param("symbols", "USD,EURO").accept("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Symbols must be comma-separated 3-letter currency codes, e.g. USD,EUR"));
    }

    @Test
    void getFxHistory_default_returns200() throws Exception {
        when(marketFxService.getFxHistory("USD", "1M")).thenReturn(history("USD", "1M"));

        mockMvc.perform(get("/api/market/fx/history").param("symbol", "USD").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("FX history retrieved"))
                .andExpect(jsonPath("$.data.symbol").value("USD"))
                .andExpect(jsonPath("$.data.quoteCurrency").value("TRY"))
                .andExpect(jsonPath("$.data.range").value("1M"))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].close").value(31.80));

        verify(marketFxService).getFxHistory("USD", "1M");
    }

    @Test
    void getFxHistory_passesRangeThrough() throws Exception {
        when(marketFxService.getFxHistory("EUR", "1Y")).thenReturn(history("EUR", "1Y"));

        mockMvc.perform(get("/api/market/fx/history")
                        .param("symbol", "EUR")
                        .param("range", "1Y")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("1Y"));

        verify(marketFxService).getFxHistory("EUR", "1Y");
    }
}
