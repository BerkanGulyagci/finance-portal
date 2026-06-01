package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
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
 * HTTP-katmanı (MockMvc) testi: {@link CryptoController} (standaloneSetup).
 * Kapsam: GET / (sayfalı liste + default param geçişi), GET /all,
 * GET /{symbol}/candles (dolu + boş-liste mesaj dalı).
 *
 * <p>NOT: Mevcut {@code CryptoControllerBinanceCandlesTest} @WebMvcTest slice'ında @Disabled.
 * Bu sınıf saf controller davranışını standaloneSetup ile izole eder
 * (bkz. {@link MarketMoversControllerTest} açıklaması).
 */
class CryptoControllerStandaloneTest {

    @Mock CryptoMarketService cryptoMarketService;
    @Mock CryptoBinanceChartService cryptoBinanceChartService;
    @Mock CryptoYahooChartService cryptoYahooChartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new CryptoController(
                cryptoMarketService, cryptoBinanceChartService, cryptoYahooChartService)).build();
    }

    private CryptoMarketItem sampleItem() {
        return new CryptoMarketItem(
                "bitcoin", "btc", "Bitcoin", "http://img/btc.png",
                new BigDecimal("3000000"), new BigDecimal("60000000000"), 1,
                new BigDecimal("1000000000"), new BigDecimal("3100000"), new BigDecimal("2900000"),
                new BigDecimal("50000"), new BigDecimal("1.67"), new BigDecimal("0.25"),
                new BigDecimal("4.10"), "2026-05-31T00:00:00Z");
    }

    @Test
    void getCryptos_default_returns200_andPassesDefaultParams() throws Exception {
        // controller defaults: page=0, size=100, currency=try
        when(cryptoMarketService.getCryptos(0, 100, "try")).thenReturn(List.of(sampleItem()));

        mockMvc.perform(get("/api/market/crypto").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Crypto market list retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("bitcoin"))
                .andExpect(jsonPath("$.data[0].symbol").value("btc"))
                .andExpect(jsonPath("$.data[0].marketCapRank").value(1))
                .andExpect(jsonPath("$.data[0].currentPrice").value(3000000));

        verify(cryptoMarketService).getCryptos(0, 100, "try");
    }

    @Test
    void getAllCoins_passesCurrencyParam() throws Exception {
        when(cryptoMarketService.getAllCoins("usd")).thenReturn(List.of(sampleItem()));

        mockMvc.perform(get("/api/market/crypto/all")
                        .param("currency", "usd")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All coins retrieved"))
                .andExpect(jsonPath("$.data[0].id").value("bitcoin"));

        verify(cryptoMarketService).getAllCoins("usd");
    }

    @Test
    void getChartCandles_withData_returns200() throws Exception {
        when(cryptoBinanceChartService.getChartCandles("btc", "5y", "try"))
                .thenReturn(List.of(new CryptoChartCandle(
                        1_700_000_000L, BigDecimal.ONE, BigDecimal.TEN,
                        BigDecimal.ONE, new BigDecimal("5"), BigDecimal.ZERO, 1_700_086_399_999L)));

        mockMvc.perform(get("/api/market/crypto/btc/candles")
                        .param("range", "5y")
                        .param("currency", "try")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Binance TRY chart candles retrieved"))
                .andExpect(jsonPath("$.data[0].timestamp").value(1_700_000_000L))
                .andExpect(jsonPath("$.data[0].close").value(5));
    }

    @Test
    void getChartCandles_emptyList_returns200_withFallbackMessage() throws Exception {
        when(cryptoBinanceChartService.getChartCandles("btc", "1y", "try")).thenReturn(List.of());

        mockMvc.perform(get("/api/market/crypto/btc/candles")
                        .param("range", "1y")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("No Binance TRY candles for this symbol/range (use Yahoo fallback on client)"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
