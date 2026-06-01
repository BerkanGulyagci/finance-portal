package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.commodity.CommodityDto;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link CommodityController}.
 * Kapsam: GET /list (200 + body shape), GET /spot (200) ve hatalı sembolde 400
 * (controller IllegalArgumentException'ı kendi yakalayıp badRequest döner).
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class CommodityControllerTest {

    @Mock YahooCommodityService commodityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new CommodityController(commodityService)).build();
    }

    private CommodityDto sampleListItem() {
        CommodityDto dto = new CommodityDto();
        dto.setSymbol("CL=F");
        dto.setDisplayNameTr("Ham Petrol");
        dto.setDisplayNameEn("Crude Oil");
        dto.setCategory("ENERGY");
        dto.setUnit("USD/bbl");
        dto.setEnabled(true);
        dto.setSource("YAHOO");
        return dto;
    }

    private CommoditySpotDto sampleSpot() {
        CommoditySpotDto dto = new CommoditySpotDto();
        dto.setSymbol("CL=F");
        dto.setDisplayNameTr("Ham Petrol");
        dto.setDisplayPrice(new BigDecimal("78.42"));
        dto.setDisplayCurrency("USD");
        dto.setChangePercent(new BigDecimal("1.35"));
        return dto;
    }

    @Test
    void list_returns200_withCommodityArray() throws Exception {
        when(commodityService.listEnabledCommodities()).thenReturn(List.of(sampleListItem()));

        mockMvc.perform(get("/api/commodities/list").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Commodity list retrieved"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbol").value("CL=F"))
                .andExpect(jsonPath("$.data[0].displayNameEn").value("Crude Oil"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void spot_returns200_withSpotShape() throws Exception {
        when(commodityService.getSpot("CL=F")).thenReturn(sampleSpot());

        mockMvc.perform(get("/api/commodities/spot").param("symbol", "CL=F").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("CL=F"))
                .andExpect(jsonPath("$.data.displayPrice").value(78.42))
                .andExpect(jsonPath("$.data.displayCurrency").value("USD"))
                .andExpect(jsonPath("$.data.changePercent").value(1.35));
    }

    @Test
    void spot_unknownSymbol_returns400() throws Exception {
        when(commodityService.getSpot(anyString()))
                .thenThrow(new IllegalArgumentException("Unknown commodity symbol"));

        mockMvc.perform(get("/api/commodities/spot").param("symbol", "BOGUS").accept("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unknown commodity symbol"));
    }
}
