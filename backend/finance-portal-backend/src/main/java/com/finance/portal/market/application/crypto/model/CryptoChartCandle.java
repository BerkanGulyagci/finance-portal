package com.finance.portal.market.application.crypto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * OHLC mum — frontend candle formatı: timestamp (saniye), open, high, low, close, volume.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CryptoChartCandle {

    private long timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;

    /** Binance closeTime (ms) — pagination; API yanıtında yok. */
    @JsonIgnore
    private long closeTimeMs;
}
