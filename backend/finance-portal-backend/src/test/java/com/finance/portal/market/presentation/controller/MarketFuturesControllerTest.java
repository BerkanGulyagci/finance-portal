package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.futures.FuturesPageResponse;
import com.finance.portal.market.application.futures.FuturesQueryService;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.presentation.mapper.MarketViopPresentationMapper;
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
 * HTTP-katmanı (MockMvc) testi: {@link MarketFuturesController}.
 * Kapsam: GET / (sayfalı liste), GET /viop/contracts (kontrat listesi),
 * GET /viop/chart (mapper geçişi + boş-veri mesaj dalı).
 *
 * <p>Gerçek (bağımsız) {@link MarketViopPresentationMapper} kullanılır — saf POJO mapper.
 * standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class MarketFuturesControllerTest {

    @Mock FuturesQueryService futuresQueryService;
    @Mock StockQueryService stockQueryService;
    @Mock ViopService viopService;
    @Mock ViopChartService viopChartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MarketViopPresentationMapper mapper = new MarketViopPresentationMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketFuturesController(
                futuresQueryService, stockQueryService, viopService, viopChartService, mapper)).build();
    }

    private FuturesPageResponse samplePage() {
        StockSummary s = new StockSummary();
        s.setSymbol("XU030D");
        s.setName("BIST 30 Vadeli");
        s.setPrice(new BigDecimal("9500.00"));
        FuturesPageResponse p = new FuturesPageResponse();
        p.setContent(List.of(s));
        p.setPage(0);
        p.setSize(20);
        p.setTotalElements(1);
        p.setTotalPages(1);
        return p;
    }

    private ViopContract sampleContract() {
        ViopContract c = new ViopContract();
        c.setName("THYAO (30 Haz 26) Vadeli FIZ.");
        c.setLastPrice("312.50");
        c.setChangePercent("1.25");
        c.setHigh("315.00");
        c.setLow("310.00");
        return c;
    }

    @Test
    void getFuturesPage_default_returns200_withPageShape() throws Exception {
        when(futuresQueryService.getPagedFutures(0, 20)).thenReturn(samplePage());

        mockMvc.perform(get("/api/market/futures").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Futures page retrieved successfully"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].symbol").value("XU030D"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(futuresQueryService).getPagedFutures(0, 20);
    }

    @Test
    void getViopContracts_returns200_withContractArray() throws Exception {
        when(viopService.getAllContracts()).thenReturn(List.of(sampleContract()));

        mockMvc.perform(get("/api/market/futures/viop/contracts").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("VIOP contracts retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("THYAO (30 Haz 26) Vadeli FIZ."))
                .andExpect(jsonPath("$.data[0].lastPrice").value("312.50"));
    }

    @Test
    void getViopChart_withPoints_returns200_andMapsThrough() throws Exception {
        ViopChartPoint pt = new ViopChartPoint(1_700_000_000L, "2026-05-15 10:00", new BigDecimal("312.50"));
        when(viopChartService.getChart("THYAO Vadeli", ViopChartPeriod.ONE_MONTH))
                .thenReturn(List.of(pt));

        mockMvc.perform(get("/api/market/futures/viop/chart")
                        .param("name", "THYAO Vadeli")
                        .param("period", "ONE_MONTH")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("VIOP chart data retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].value").value(312.50));

        verify(viopChartService).getChart("THYAO Vadeli", ViopChartPeriod.ONE_MONTH);
    }

    @Test
    void getViopChart_emptyPoints_returns200_withNoDataMessage() throws Exception {
        // period verilmediğinde default ONE_WEEK; boş liste → "bulunamadı" mesajı.
        when(viopChartService.getChart("BOGUS", ViopChartPeriod.ONE_WEEK)).thenReturn(List.of());

        mockMvc.perform(get("/api/market/futures/viop/chart")
                        .param("name", "BOGUS")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Bu dönem için VİOP grafik verisi bulunamadı."))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
