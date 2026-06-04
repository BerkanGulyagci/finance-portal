package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.movers.MarketMoversService;
import com.finance.portal.market.application.movers.model.MarketMover;
import com.finance.portal.market.application.movers.model.MoversCategory;
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
 * HTTP-katmanı (MockMvc) testi: {@link MarketMoversController}.
 *
 * <p>Bu repo'da Spring Security filter-chain + OAuth2 resource-server, {@code @WebMvcTest}
 * slice'ını kırılgan yapıyor (custom filter @Component'leri + JWK URI). Mevcut testler de
 * full {@code @SpringBootTest}+Testcontainers kullanıyor. Saf controller HTTP davranışını
 * (status + JSON body shape) izole etmek için MockMvc {@code standaloneSetup} kullanılır:
 * gerçek DispatcherServlet + Jackson + ApiResponse serileştirmesi devrede, ama context/güvenlik yok.
 */
class MarketMoversControllerTest {

    @Mock MarketMoversService marketMoversService;

    private MockMvc mockMvc;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketMoversController(marketMoversService))
                .build();
    }

    private MoversCategory sampleCategory() {
        MarketMover gainer = new MarketMover(
                "CRYPTO", "bitcoin", "BTC", "Bitcoin",
                new BigDecimal("1000000"), "TRY", new BigDecimal("5.25"), "http://img/btc.png");
        MarketMover loser = new MarketMover(
                "CRYPTO", "ethereum", "ETH", "Ethereum",
                new BigDecimal("50000"), "TRY", new BigDecimal("-3.10"), "http://img/eth.png");
        return new MoversCategory("crypto", "Kripto", List.of(gainer), List.of(loser));
    }

    @Test
    void getMovers_returns200_withCategoryShape() throws Exception {
        when(marketMoversService.getMovers(5)).thenReturn(List.of(sampleCategory()));

        mockMvc.perform(get("/api/v1/market/movers").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].key").value("crypto"))
                .andExpect(jsonPath("$.data[0].label").value("Kripto"))
                .andExpect(jsonPath("$.data[0].gainers[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.data[0].gainers[0].changePercent").value(5.25))
                .andExpect(jsonPath("$.data[0].losers[0].symbol").value("ETH"));
    }

    @Test
    void getMovers_clampsLimitToMax20() throws Exception {
        // limit=100 -> safeLimit=20 (controller clamp). Servis tam 20 ile çağrılmalı.
        when(marketMoversService.getMovers(20)).thenReturn(List.of(sampleCategory()));

        mockMvc.perform(get("/api/v1/market/movers").param("limit", "100").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("crypto"));

        verify(marketMoversService).getMovers(20);
    }

    @Test
    void getMovers_clampsLimitToMin1() throws Exception {
        // limit=0 -> safeLimit=1 (controller clamp).
        when(marketMoversService.getMovers(1)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market/movers").param("limit", "0").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(marketMoversService).getMovers(1);
    }
}
