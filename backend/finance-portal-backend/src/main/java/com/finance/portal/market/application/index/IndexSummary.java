package com.finance.portal.market.application.index;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * BIST endeks listesi satırı — kod, ad, kategori + Yahoo'dan canlı fiyat/değişim.
 * Hacim/piyasa değeri YOK (Yahoo endeksler için vermez: regularMarketVolume=0, marketCap alanı yok).
 */
@Getter
@Setter
@NoArgsConstructor
public class IndexSummary {

    private String code;          // XU100
    private String symbol;        // XU100.IS (Yahoo)
    private String name;          // BIST 100
    private String category;      // Ana | Sektör | Katılım | Tema
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private String currency;      // TRY
}
