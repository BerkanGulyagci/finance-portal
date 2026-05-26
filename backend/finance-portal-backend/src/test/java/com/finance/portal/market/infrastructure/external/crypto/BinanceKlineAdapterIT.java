package com.finance.portal.market.infrastructure.external.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.port.BinanceKlinePort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BinanceKlineAdapter <-> BinanceKlineClient zincirinin Binance JSON
 * yanıtını doğru parse ettiğini doğrular. Gerçek api.binance.com'a istek
 * atmaz; WireMock stub'lanmış bir kline yanıtı döner — test deterministik,
 * offline ve region-blok'tan etkilenmez.
 */
class BinanceKlineAdapterIT {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchBtcTryDaily_parsesStubbedKlines() {
        // Binance /api/v3/klines yanıt formatı: array of arrays.
        // Sütunlar: [openTime, open, high, low, close, volume, closeTime, quoteVolume,
        //            trades, takerBuyBaseVolume, takerBuyQuoteVolume, ignore]
        String stubbedKlinesJson = """
                [
                  [1700000000000, "50000.00", "51000.00", "49000.00", "50500.00", "1.5",
                   1700086399999, "75750.00", 100, "0.8", "40000.00", "0"],
                  [1700086400000, "50500.00", "52000.00", "50000.00", "51500.00", "2.1",
                   1700172799999, "108150.00", 150, "1.2", "60000.00", "0"]
                ]
                """;

        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v3/klines"))
                .willReturn(WireMock.okJson(stubbedKlinesJson)));

        BinanceKlinePort port = new BinanceKlineAdapter(
                new BinanceKlineClient(wireMock.baseUrl(), new ObjectMapper(),
                        new CentralIntegrationLogService(null, null)));

        long fiveYearsAgo = System.currentTimeMillis() - 5L * 365L * 86_400_000L;
        List<CryptoChartCandle> batch = port.fetchKlinesPage("BTCTRY", "1d", 1000, fiveYearsAgo);

        assertThat(batch).hasSize(2);
        assertThat(batch.get(0).getClose()).isEqualByComparingTo("50500.00");
        assertThat(batch.get(0).getCloseTimeMs()).isEqualTo(1_700_086_399_999L);
        assertThat(batch.get(1).getClose()).isEqualByComparingTo("51500.00");
    }
}
