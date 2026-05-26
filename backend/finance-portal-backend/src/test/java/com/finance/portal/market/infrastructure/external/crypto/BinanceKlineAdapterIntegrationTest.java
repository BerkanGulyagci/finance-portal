package com.finance.portal.market.infrastructure.external.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.port.BinanceKlinePort;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gerçek Binance API — ağ gerektirir.
 */
@Disabled("Hits the real Binance API; flaky/region-blocked on CI. Will be replaced by a WireMock-based test in Phase 2.")
class BinanceKlineAdapterIntegrationTest {

    @Test
    void fetchBtcTryDaily_returnsCandles() {
        BinanceKlinePort port = new BinanceKlineAdapter(
                new BinanceKlineClient("https://api.binance.com", new ObjectMapper(),
                        new CentralIntegrationLogService(null, null)));

        long fiveYearsAgo = System.currentTimeMillis() - 5L * 365L * 86_400_000L;
        List<CryptoChartCandle> batch = port.fetchKlinesPage("BTCTRY", "1d", 1000, fiveYearsAgo);

        assertThat(batch).isNotEmpty();
        assertThat(batch.get(0).getClose()).isNotNull();
        assertThat(batch.get(0).getCloseTimeMs()).isPositive();
    }
}
