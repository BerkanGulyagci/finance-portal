package com.finance.portal.market.application.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoYahooChartServiceTest {

    @Test
    void shouldUseYahoo_onlyUsdEurFiveYearAndMax() {
        assertThat(CryptoYahooChartService.shouldUseYahoo("usd", "5y")).isTrue();
        assertThat(CryptoYahooChartService.shouldUseYahoo("EUR", "max")).isTrue();
        assertThat(CryptoYahooChartService.shouldUseYahoo("try", "5y")).isFalse();
        assertThat(CryptoYahooChartService.shouldUseYahoo("usd", "365")).isFalse();
    }

    @Test
    void toYahooSymbol_buildsPair() {
        assertThat(CryptoYahooChartService.toYahooSymbol("btc", "usd")).isEqualTo("BTC-USD");
        assertThat(CryptoYahooChartService.toYahooSymbol("eth", "eur")).isEqualTo("ETH-EUR");
    }

    @Test
    void resolveYahooRangeInterval_maps5yAndMax() {
        assertThat(CryptoYahooChartService.resolveYahooRangeInterval("5y")).containsExactly("5y", "1d");
        assertThat(CryptoYahooChartService.resolveYahooRangeInterval("max")).containsExactly("max", "1d");
    }
}
