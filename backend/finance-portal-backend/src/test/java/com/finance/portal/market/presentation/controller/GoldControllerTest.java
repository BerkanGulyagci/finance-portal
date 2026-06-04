package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link GoldController}.
 * Kapsam: GET /spot (200 + body shape), GET /history (200 + default param geçişi).
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class GoldControllerTest {

    @Mock GoldMarketService goldMarketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new GoldController(goldMarketService)).build();
    }

    private GoldSpotResponse sampleSpot() {
        GoldSpotResponse dto = new GoldSpotResponse();
        dto.setSource("Borsa İstanbul");
        dto.setOfficial(true);
        dto.setGramGoldTry(new BigDecimal("2450.75"));
        dto.setOnsUsd(new BigDecimal("2350.10"));
        dto.setUsdTry(new BigDecimal("32.50"));
        dto.setCurrency("TRY");
        return dto;
    }

    private GoldHistoryResponse sampleHistory(String range, String currency) {
        GoldHistoryResponse dto = new GoldHistoryResponse();
        dto.setSymbol("XAU");
        dto.setRange(range);
        dto.setCurrency(currency);
        dto.setSource("Borsa İstanbul");
        GoldHistoryPoint p = new GoldHistoryPoint("2026-05-01", new BigDecimal("2400.00"));
        dto.setPoints(List.of(p));
        return dto;
    }

    @Test
    void spot_returns200_withSpotShape() throws Exception {
        when(goldMarketService.getSpotGold()).thenReturn(sampleSpot());

        mockMvc.perform(get("/api/v1/gold/spot").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gold spot data retrieved"))
                .andExpect(jsonPath("$.data.source").value("Borsa İstanbul"))
                .andExpect(jsonPath("$.data.official").value(true))
                .andExpect(jsonPath("$.data.gramGoldTry").value(2450.75))
                .andExpect(jsonPath("$.data.onsUsd").value(2350.10))
                .andExpect(jsonPath("$.data.currency").value("TRY"));
    }

    @Test
    void history_usesDefaultParams_whenNotProvided() throws Exception {
        // controller defaults: range=1M, currency=USD
        when(goldMarketService.getGoldHistory("1M", "USD")).thenReturn(sampleHistory("1M", "USD"));

        mockMvc.perform(get("/api/v1/gold/history").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gold history retrieved"))
                .andExpect(jsonPath("$.data.range").value("1M"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].close").value(2400.00));

        verify(goldMarketService).getGoldHistory("1M", "USD");
    }

    @Test
    void history_passesQueryParamsThrough() throws Exception {
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(sampleHistory("1Y", "TRY"));

        mockMvc.perform(get("/api/v1/gold/history")
                        .param("range", "1Y")
                        .param("currency", "TRY")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("1Y"))
                .andExpect(jsonPath("$.data.currency").value("TRY"));

        verify(goldMarketService).getGoldHistory("1Y", "TRY");
    }
}
