package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.economy.DepositRateService;
import com.finance.portal.market.application.economy.EconomyChartService;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.LoanRateService;
import com.finance.portal.market.application.economy.model.DepositRates;
import com.finance.portal.market.application.economy.model.EconomyChartPoint;
import com.finance.portal.market.application.economy.model.EconomyChartSeries;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.LoanRates;
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
 * HTTP-katmanı (MockMvc) testi: {@link EconomyController}.
 * Kapsam: GET / (özet — kategoriye göre gruplama + DTO map), GET /series (full=true/false
 * dallanması), GET /charts, GET /loan-rates, GET /deposit-rates ve boş-gösterge edge-path.
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class EconomyControllerTest {

    @Mock EconomyService economyService;
    @Mock EconomyChartService economyChartService;
    @Mock LoanRateService loanRateService;
    @Mock DepositRateService depositRateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new EconomyController(economyService, economyChartService, loanRateService, depositRateService))
                .build();
    }

    private EconomyIndicator indicator(String key, String label, String category, String categoryLabel) {
        EconomyIndicator i = new EconomyIndicator();
        i.setKey(key);
        i.setLabel(label);
        i.setCategory(category);
        i.setCategoryLabel(categoryLabel);
        i.setUnit("%");
        i.setFrequency("MONTHLY");
        i.setSeriesCode("TP.X");
        i.setValue(new BigDecimal("42.12"));
        i.setPeriod("2026-4");
        i.setPreviousValue(new BigDecimal("40.00"));
        i.setChangePercent(new BigDecimal("5.30"));
        i.setAbsoluteChange(new BigDecimal("2.12"));
        i.setYoyChangePercent(new BigDecimal("35.10"));
        i.setAvailable(true);
        i.setPreferAbsolute(false);
        return i;
    }

    private EconomyChartSeries series(String key, String label) {
        return new EconomyChartSeries(key, label, "%", "MONTHLY", "yoy", "TCMB EVDS",
                List.of(new EconomyChartPoint("2026-4", new BigDecimal("35.10"))));
    }

    @Test
    void getEconomySummary_returns200_groupedByCategory() throws Exception {
        // İki ayrı kategoride iki gösterge → 2 grup, ilk grupta bir item
        EconomyIndicator infl = indicator("tufe", "TÜFE", "INFLATION", "Enflasyon");
        EconomyIndicator rate = indicator("policy", "Politika Faizi", "RATES", "Faizler");
        when(economyService.getSummary()).thenReturn(List.of(infl, rate));

        mockMvc.perform(get("/api/v1/market/economy").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Türkiye ekonomi göstergeleri"))
                .andExpect(jsonPath("$.data.source").value(EconomyService.SOURCE))
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].category").value("INFLATION"))
                .andExpect(jsonPath("$.data.groups[0].label").value("Enflasyon"))
                .andExpect(jsonPath("$.data.groups[0].indicators.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].indicators[0].key").value("tufe"))
                .andExpect(jsonPath("$.data.groups[0].indicators[0].value").value(42.12))
                .andExpect(jsonPath("$.data.groups[0].indicators[0].available").value(true))
                .andExpect(jsonPath("$.data.groups[1].category").value("RATES"))
                .andExpect(jsonPath("$.data.groups[1].indicators[0].key").value("policy"));
    }

    @Test
    void getEconomySummary_emptyIndicators_returns200_withNoGroups() throws Exception {
        when(economyService.getSummary()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market/economy").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groups.length()").value(0));
    }

    @Test
    void getSeries_default_usesChartSeries() throws Exception {
        when(economyChartService.getChartSeries("tufe")).thenReturn(series("tufe", "TÜFE"));

        mockMvc.perform(get("/api/v1/market/economy/series").param("key", "tufe").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gösterge zaman serisi"))
                .andExpect(jsonPath("$.data.key").value("tufe"))
                .andExpect(jsonPath("$.data.transform").value("yoy"))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].value").value(35.10));

        verify(economyChartService).getChartSeries("tufe");
    }

    @Test
    void getSeries_full_usesFullChartSeries() throws Exception {
        when(economyChartService.getFullChartSeries("ufe")).thenReturn(series("ufe", "ÜFE"));

        mockMvc.perform(get("/api/v1/market/economy/series")
                        .param("key", "ufe")
                        .param("full", "true")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("ufe"));

        verify(economyChartService).getFullChartSeries("ufe");
    }

    @Test
    void getAllSeries_returns200_withSeriesArray() throws Exception {
        when(economyChartService.getAllChartSeries())
                .thenReturn(List.of(series("tufe", "TÜFE"), series("ufe", "ÜFE")));

        mockMvc.perform(get("/api/v1/market/economy/charts").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tüm gösterge zaman serileri"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].key").value("tufe"))
                .andExpect(jsonPath("$.data[1].key").value("ufe"));
    }

    @Test
    void getLoanRates_returns200_withRateShape() throws Exception {
        LoanRates lr = new LoanRates();
        lr.setPersonal(new BigDecimal("55.10"));
        lr.setVehicle(new BigDecimal("48.20"));
        lr.setHousing(new BigDecimal("39.90"));
        lr.setCommercial(new BigDecimal("52.00"));
        lr.setPeriod("08-05-2026");
        lr.setSource("TCMB EVDS");
        when(loanRateService.getLoanRates()).thenReturn(lr);

        mockMvc.perform(get("/api/v1/market/economy/loan-rates").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Güncel kredi faiz oranları"))
                .andExpect(jsonPath("$.data.personal").value(55.10))
                .andExpect(jsonPath("$.data.housing").value(39.90))
                .andExpect(jsonPath("$.data.period").value("08-05-2026"))
                .andExpect(jsonPath("$.data.source").value("TCMB EVDS"));
    }

    @Test
    void getDepositRates_returns200_withRateShape() throws Exception {
        DepositRates dr = new DepositRates();
        dr.setUpTo1Month(new BigDecimal("45.00"));
        dr.setUpTo3Months(new BigDecimal("47.50"));
        dr.setUpTo6Months(new BigDecimal("49.00"));
        dr.setUpTo1Year(new BigDecimal("50.25"));
        dr.setInflationYoy(new BigDecimal("35.10"));
        dr.setStopaj6m(new BigDecimal("10"));
        dr.setStopaj1y(new BigDecimal("7.5"));
        dr.setStopajOver1y(new BigDecimal("5"));
        dr.setPeriod("2026-4");
        dr.setSource("TCMB EVDS");
        when(depositRateService.getDepositRates()).thenReturn(dr);

        mockMvc.perform(get("/api/v1/market/economy/deposit-rates").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Güncel mevduat faiz oranları"))
                .andExpect(jsonPath("$.data.upTo1Month").value(45.00))
                .andExpect(jsonPath("$.data.upTo1Year").value(50.25))
                .andExpect(jsonPath("$.data.inflationYoy").value(35.10))
                .andExpect(jsonPath("$.data.stopaj1y").value(7.5));
    }
}
