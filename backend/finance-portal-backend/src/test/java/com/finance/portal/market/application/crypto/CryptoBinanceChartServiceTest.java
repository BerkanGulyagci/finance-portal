package com.finance.portal.market.application.crypto;

import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.port.BinanceKlinePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CryptoBinanceChartServiceTest {

    @Test
    void shouldUseBinance_onlyTryFiveYearAndMax() {
        assertThat(CryptoBinanceChartService.shouldUseBinance("try", "5y")).isTrue();
        assertThat(CryptoBinanceChartService.shouldUseBinance("TRY", "max")).isTrue();
        assertThat(CryptoBinanceChartService.shouldUseBinance("try", "all")).isTrue();
        assertThat(CryptoBinanceChartService.shouldUseBinance("usd", "5y")).isFalse();
        assertThat(CryptoBinanceChartService.shouldUseBinance("eur", "max")).isFalse();
        assertThat(CryptoBinanceChartService.shouldUseBinance("try", "365")).isFalse();
    }

    @Test
    void toBinanceTrySymbol_mapsBaseToPair() {
        assertThat(CryptoBinanceChartService.toBinanceTrySymbol("btc")).isEqualTo("BTCTRY");
        assertThat(CryptoBinanceChartService.toBinanceTrySymbol(" ETH ")).isEqualTo("ETHTRY");
    }

    @Test
    void fetchAllKlines_paginatesUntilBatchSmallerThanLimit() {
        BinanceKlinePort port = mock(BinanceKlinePort.class);
        List<CryptoChartCandle> first = batch(1000, 1_000_000L);
        long nextStart = first.get(first.size() - 1).getCloseTimeMs() + 1;

        when(port.fetchKlinesPage(eq("BTCTRY"), eq("1w"), eq(1000), eq(0L)))
                .thenReturn(first);
        when(port.fetchKlinesPage(eq("BTCTRY"), eq("1w"), eq(1000), eq(nextStart)))
                .thenReturn(batch(50, 2_000_000L));

        CryptoBinanceChartService service = new CryptoBinanceChartService(port);
        List<CryptoChartCandle> result = service.getChartCandles("btc", "max", "try");

        assertThat(result).hasSize(1050);
    }

    private static List<CryptoChartCandle> batch(int size, long baseOpenMs) {
        List<CryptoChartCandle> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long openMs = baseOpenMs + (long) i * 86_400_000L;
            long closeMs = openMs + 86_399_999L;
            list.add(new CryptoChartCandle(
                    openMs / 1000,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    BigDecimal.valueOf(5),
                    BigDecimal.ZERO,
                    closeMs
            ));
        }
        return list;
    }
}
