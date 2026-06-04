package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-katmanı (MockMvc) testi: {@link EvdsBondController} (admin olmayan uçlar).
 * Kapsam: GET / (sayfalı/sıralı liste), GET /categories (dağılım), GET /{code} detay (200),
 * detay null → 404, geçersiz sortBy → 400.
 *
 * <p>standaloneSetup ile izole edilir (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class EvdsBondControllerTest {

    @Mock EvdsBondService evdsBondService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvdsBondController(evdsBondService)).build();
    }

    private EvdsBondInstrument sampleBond(String code) {
        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setInstrumentCode(code);
        b.setType("Devlet Tahvili");
        b.setCategory(BondCategory.FIXED_COUPON_BOND);
        b.setIssueDate(LocalDate.of(2024, 1, 10));
        b.setMaturityDate(LocalDate.of(2027, 1, 10));
        b.setRemainingDays(400);
        b.setIndicatorValue(new BigDecimal("98.50"));
        b.setPreviousValue(new BigDecimal("98.10"));
        b.setDailyChangePercent(new BigDecimal("0.41"));
        b.setCouponRate(new BigDecimal("12.00"));
        b.setSource("TCMB EVDS");
        return b;
    }

    @Test
    void getBonds_returns200_withPagedShape() throws Exception {
        when(evdsBondService.getEvdsBondsAll())
                .thenReturn(List.of(sampleBond("TRD070727K10"), sampleBond("TRD120128K11")));

        mockMvc.perform(get("/api/v1/market/bonds/evds")
                        .param("page", "0")
                        .param("size", "50")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].source").value("TCMB EVDS"));
    }

    @Test
    void getCategoryCounts_returns200_withDistribution() throws Exception {
        when(evdsBondService.getEvdsBondsAll())
                .thenReturn(List.of(sampleBond("A"), sampleBond("B")));

        mockMvc.perform(get("/api/v1/market/bonds/evds/categories").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.FIXED_COUPON_BOND").value(2));
    }

    @Test
    void getBondDetail_returns200_andNormalizesCode() throws Exception {
        // path "trd070727k10" → normalize "TRD070727K10"
        when(evdsBondService.getEvdsBondDetail("TRD070727K10"))
                .thenReturn(sampleBond("TRD070727K10"));

        mockMvc.perform(get("/api/v1/market/bonds/evds/trd070727k10").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.instrumentCode").value("TRD070727K10"))
                .andExpect(jsonPath("$.data.indicatorValue").value(98.50))
                .andExpect(jsonPath("$.data.couponRate").value(12.00));
    }

    @Test
    void getBondDetail_nullFromService_returns404() throws Exception {
        // Not: controller normalizeCode() locale-bağımsız olmayan toUpperCase() kullanır.
        // Türkçe locale'de 'i'→'İ' olur; bu yüzden 'i' içermeyen bir kod seçilir.
        when(evdsBondService.getEvdsBondDetail("TRDXXXX")).thenReturn(null);

        mockMvc.perform(get("/api/v1/market/bonds/evds/trdxxxx").accept("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Kıymet bulunamadı: TRDXXXX"));
    }

    @Test
    void getBonds_invalidSortBy_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/bonds/evds")
                        .param("sortBy", "bogusField")
                        .accept("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
