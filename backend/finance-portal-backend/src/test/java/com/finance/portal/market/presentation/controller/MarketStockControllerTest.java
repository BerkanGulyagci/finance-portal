package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockPageResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
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
 * HTTP-katmanı (MockMvc) testi: {@link MarketStockController}.
 * Kapsam: GET / (sayfalı liste, default + index param dalları), GET /{symbol}
 * (detay + sembol normalize/uppercase doğrulaması).
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class MarketStockControllerTest {

    @Mock StockQueryService stockQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketStockController(stockQueryService)).build();
    }

    private StockSummary sampleSummary() {
        StockSummary s = new StockSummary();
        s.setSymbol("THYAO.IS");
        s.setName("Türk Hava Yolları");
        s.setCurrency("TRY");
        s.setExchange("BIST");
        s.setPrice(new BigDecimal("312.50"));
        s.setChangePercent(new BigDecimal("1.25"));
        return s;
    }

    private StockPageResponse samplePage() {
        StockPageResponse p = new StockPageResponse();
        p.setContent(List.of(sampleSummary()));
        p.setPage(0);
        p.setSize(20);
        p.setTotalElements(1);
        p.setTotalPages(1);
        return p;
    }

    private StockDetail sampleDetail() {
        StockDetail d = new StockDetail();
        d.setSymbol("THYAO.IS");
        d.setName("Türk Hava Yolları");
        d.setCurrency("TRY");
        d.setExchange("BIST");
        d.setSummary(sampleSummary());
        d.setFiftyTwoWeekHigh(new BigDecimal("350.00"));
        d.setFiftyTwoWeekLow(new BigDecimal("200.00"));
        return d;
    }

    @Test
    void getStockPage_default_returns200_withPageShape() throws Exception {
        when(stockQueryService.getPagedStockSummaries(0, 20)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/market/stocks").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock page retrieved successfully"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].symbol").value("THYAO.IS"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(stockQueryService).getPagedStockSummaries(0, 20);
    }

    @Test
    void getStockPage_withIndex_usesByIndexBranch_uppercased() throws Exception {
        // index=xu030 → controller uppercase'ler ve by-index dalına gider.
        when(stockQueryService.getPagedStockSummariesByIndex(0, 20, "XU030")).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/market/stocks")
                        .param("index", "xu030")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].symbol").value("THYAO.IS"));

        verify(stockQueryService).getPagedStockSummariesByIndex(0, 20, "XU030");
    }

    @Test
    void getStockDetail_returns200_andNormalizesSymbol() throws Exception {
        // path "thyao" (lowercase) → controller normalize → "THYAO"
        // (nokta içeren sembol standalone MockMvc'de uzantı sanılıp kesilir; bu yüzden noktasız sembol)
        when(stockQueryService.getStockDetail("THYAO")).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/market/stocks/thyao").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock detail retrieved successfully"))
                .andExpect(jsonPath("$.data.symbol").value("THYAO.IS"))
                .andExpect(jsonPath("$.data.currency").value("TRY"))
                .andExpect(jsonPath("$.data.fiftyTwoWeekHigh").value(350.00))
                .andExpect(jsonPath("$.data.fiftyTwoWeekLow").value(200.00));

        verify(stockQueryService).getStockDetail("THYAO");
    }
}
