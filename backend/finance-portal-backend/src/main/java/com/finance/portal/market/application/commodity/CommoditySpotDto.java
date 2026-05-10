package com.finance.portal.market.application.commodity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * /api/commodities/spot endpoint'i için spot fiyat DTO.
 *
 * Ham (raw) değerler Yahoo'dan gelen orijinal değerlerdir.
 * Display değerler USX → USD dönüşümü uygulanmış son değerlerdir.
 */
@Data
public class CommoditySpotDto {

    private String  symbol;
    private String  displayNameTr;
    private String  displayNameEn;
    private String  category;
    private String  categoryDisplayTr;
    private String  unit;

    // Ham Yahoo verisi
    private BigDecimal rawPrice;
    private String     rawCurrency;

    // Gösterim değerleri (USX → USD dönüşümü uygulanmış)
    private BigDecimal displayPrice;
    private String     displayCurrency;
    private boolean    centConverted;

    // Değişim
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal previousClose;

    // Gün içi
    private BigDecimal dayHigh;
    private BigDecimal dayLow;

    // 52 hafta
    private BigDecimal weekHigh52;
    private BigDecimal weekLow52;

    private Long   volume;
    private String lastUpdated;

    // Meta
    private String  source;
    private String  dataType;
    private boolean stale;
}
