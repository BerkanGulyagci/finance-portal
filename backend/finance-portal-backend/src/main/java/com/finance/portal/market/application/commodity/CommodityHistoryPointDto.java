package com.finance.portal.market.application.commodity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Tek bir tarihsel OHLC noktası.
 */
@Data
public class CommodityHistoryPointDto {

    private long   timestamp;   // Unix epoch (saniye)
    private String date;        // yyyy-MM-dd

    // Ham Yahoo değerleri
    private BigDecimal rawOpen;
    private BigDecimal rawHigh;
    private BigDecimal rawLow;
    private BigDecimal rawClose;
    private String     rawCurrency;

    // Gösterim değerleri (USX → USD dönüşümü uygulanmış)
    private BigDecimal displayOpen;
    private BigDecimal displayHigh;
    private BigDecimal displayLow;
    private BigDecimal displayClose;
    private String     displayCurrency;

    private Long    volume;
    private String  unit;
    private boolean centConverted;
}
