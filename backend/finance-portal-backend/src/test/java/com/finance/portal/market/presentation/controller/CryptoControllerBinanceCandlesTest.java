package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CryptoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Disabled("@WebMvcTest slice tüm proje filtrelerini (DisabledAccountFilter, GlobalExceptionHandler) " +
        "yüklediği için bağımlılıklar tek tek mock'lanamıyor. Faz 2'de @SpringBootTest'e dönüştürülecek.")
class CryptoControllerBinanceCandlesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CryptoMarketService cryptoMarketService;

    @MockBean
    private CryptoBinanceChartService cryptoBinanceChartService;

    @MockBean
    private CryptoYahooChartService cryptoYahooChartService;

    // DisabledAccountFilter (security) bunu ister; @WebMvcTest slice'da yüklenmez → mock.
    @MockBean
    private UserAccountStatusPort userAccountStatusPort;

    @Test
    void getChartCandles_returnsOk() throws Exception {
        when(cryptoBinanceChartService.getChartCandles(anyString(), anyString(), anyString()))
                .thenReturn(List.of(
                        new CryptoChartCandle(1_700_000_000L, BigDecimal.ONE, BigDecimal.TEN,
                                BigDecimal.ONE, BigDecimal.valueOf(5), BigDecimal.ZERO, 1_700_086_399_999L)
                ));

        mockMvc.perform(get("/api/v1/market/crypto/btc/candles")
                        .param("range", "5y")
                        .param("currency", "try"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].timestamp").value(1_700_000_000L))
                .andExpect(jsonPath("$.data[0].close").value(5));
    }
}
