package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.indicator.EconomicIndicatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link EconomicIndicatorController}.
 * {@code GET /api/market/indicators} -> ApiResponse&lt;Map&lt;String,String&gt;&gt;.
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class EconomicIndicatorControllerTest {

    @Mock EconomicIndicatorService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new EconomicIndicatorController(service)).build();
    }

    @Test
    void getIndicators_returns200_withMapPayload() throws Exception {
        Map<String, String> indicators = new LinkedHashMap<>();
        indicators.put("policyRate", "40");
        indicators.put("inflation", "32.37");
        indicators.put("ppi", "24.5");
        indicators.put("depositRate", "45");
        when(service.getIndicators()).thenReturn(indicators);

        mockMvc.perform(get("/api/v1/market/indicators").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Indicators retrieved"))
                .andExpect(jsonPath("$.data.policyRate").value("40"))
                .andExpect(jsonPath("$.data.inflation").value("32.37"))
                .andExpect(jsonPath("$.data.ppi").value("24.5"))
                .andExpect(jsonPath("$.data.depositRate").value("45"));
    }

    @Test
    void getIndicators_emptyMap_stillReturns200() throws Exception {
        when(service.getIndicators()).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/market/indicators").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
