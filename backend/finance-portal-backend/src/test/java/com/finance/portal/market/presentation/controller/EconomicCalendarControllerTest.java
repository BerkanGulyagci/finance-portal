package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.calendar.EconomicCalendarService;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link EconomicCalendarController}.
 * Kapsam: GET /calendar (200 + body shape + ISO tarih bind), eksik zorunlu param → 400.
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class EconomicCalendarControllerTest {

    @Mock EconomicCalendarService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new EconomicCalendarController(service)).build();
    }

    private EconomicCalendarEvent sampleEvent() {
        EconomicCalendarEvent e = new EconomicCalendarEvent();
        e.setTime("2026-05-15 12:30:00");
        e.setCountry("US");
        e.setCurrency("USD");
        e.setEvent("CPI YoY");
        e.setImpact("high");
        e.setActual(3.4);
        e.setEstimate(3.3);
        e.setPrev(3.2);
        e.setUnit("%");
        return e;
    }

    @Test
    void getCalendar_returns200_withEventShape() throws Exception {
        when(service.getEvents(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of(sampleEvent()));

        mockMvc.perform(get("/api/market/economy/calendar")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Ekonomik takvim olayları"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].country").value("US"))
                .andExpect(jsonPath("$.data[0].event").value("CPI YoY"))
                .andExpect(jsonPath("$.data[0].impact").value("high"))
                .andExpect(jsonPath("$.data[0].actual").value(3.4))
                .andExpect(jsonPath("$.data[0].unit").value("%"));

        verify(service).getEvents(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    }

    @Test
    void getCalendar_emptyRange_returns200_withEmptyArray() throws Exception {
        when(service.getEvents(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/market/economy/calendar")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-02")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getCalendar_missingRequiredFromParam_returns400() throws Exception {
        // 'from' zorunlu — eksik olunca MissingServletRequestParameterException → 400.
        mockMvc.perform(get("/api/market/economy/calendar")
                        .param("to", "2026-05-31")
                        .accept("application/json"))
                .andExpect(status().isBadRequest());
    }
}
