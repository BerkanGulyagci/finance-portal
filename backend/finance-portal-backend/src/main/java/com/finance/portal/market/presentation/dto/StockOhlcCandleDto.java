package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tek bir OHLC mum — hisse grafiği candle formatı.
 * JSON alanları frontend sözleşmesiyle birebir: time (epoch saniye), open, high, low, close, volume.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockOhlcCandleDto {

    private Long time;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    /** {@code StockQueryService.getStockOhlc()}'in ürettiği ham mum map'ini DTO'ya çevirir. */
    public static StockOhlcCandleDto from(Map<String, Object> candle) {
        return new StockOhlcCandleDto(
                (Long) candle.get("time"),
                (BigDecimal) candle.get("open"),
                (BigDecimal) candle.get("high"),
                (BigDecimal) candle.get("low"),
                (BigDecimal) candle.get("close"),
                (Long) candle.get("volume")
        );
    }
}
