package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
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
 * HTTP-katmanı (MockMvc) testi: {@link SilverController}.
 * Kapsam: GET /spot (200 + body shape), GET /history (200 + default param=1M/TRY geçişi
 * ve boş points listesi edge-path).
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class SilverControllerTest {

    @Mock SilverMarketService silverMarketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new SilverController(silverMarketService)).build();
    }

    private SilverSpotResponse sampleSpot() {
        SilverSpotResponse dto = new SilverSpotResponse();
        dto.setSource("Borsa İstanbul");
        dto.setOfficial(true);
        dto.setSilverGramTry(new BigDecimal("32.18"));
        dto.setSilverUsdOns(new BigDecimal("29.45"));
        dto.setBistDate("2026-05-30");
        return dto;
    }

    private SilverHistoryResponse historyWith(String range, String currency, List<SilverHistoryPoint> points) {
        SilverHistoryResponse dto = new SilverHistoryResponse();
        dto.setSymbol("XAG");
        dto.setRange(range);
        dto.setCurrency(currency);
        dto.setSource("Borsa İstanbul");
        dto.setPoints(points);
        return dto;
    }

    @Test
    void spot_returns200_withSpotShape() throws Exception {
        when(silverMarketService.getSpotSilver()).thenReturn(sampleSpot());

        mockMvc.perform(get("/api/v1/silver/spot").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Silver spot data retrieved"))
                .andExpect(jsonPath("$.data.source").value("Borsa İstanbul"))
                .andExpect(jsonPath("$.data.official").value(true))
                .andExpect(jsonPath("$.data.silverGramTry").value(32.18))
                .andExpect(jsonPath("$.data.silverUsdOns").value(29.45))
                .andExpect(jsonPath("$.data.bistDate").value("2026-05-30"));
    }

    @Test
    void history_usesDefaultParams_andReturnsPoints() throws Exception {
        // controller defaults: range=1M, currency=TRY
        SilverHistoryPoint p = new SilverHistoryPoint();
        p.setDate("2026-05-01");
        p.setClose(new BigDecimal("31.50"));
        when(silverMarketService.getSilverHistory("1M", "TRY"))
                .thenReturn(historyWith("1M", "TRY", List.of(p)));

        mockMvc.perform(get("/api/v1/silver/history").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Silver history retrieved"))
                .andExpect(jsonPath("$.data.range").value("1M"))
                .andExpect(jsonPath("$.data.currency").value("TRY"))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].close").value(31.50));

        verify(silverMarketService).getSilverHistory("1M", "TRY");
    }

    @Test
    void history_emptyPoints_returns200_withEmptyArray() throws Exception {
        when(silverMarketService.getSilverHistory("1W", "USD"))
                .thenReturn(historyWith("1W", "USD", List.of()));

        mockMvc.perform(get("/api/v1/silver/history")
                        .param("range", "1W")
                        .param("currency", "USD")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points.length()").value(0));

        verify(silverMarketService).getSilverHistory("1W", "USD");
    }
}
