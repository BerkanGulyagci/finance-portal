package com.finance.portal.portfolio.application.whatif;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portföy geneli "Ne Olurdu?" sonucu: yatırdığın paraları o tarihlerde alternatif araçlara
 * koysaydın bugün ne olurdu — gerçek portföy değerine karşı.
 *
 * <p>Hesap, mevcut pozisyonların TL maliyeti ({@code totalCost}) ve ilk alış tarihi üzerinden
 * yapılır (reel getiri ile aynı basitleştirme). Her senaryo: maliyet × (bugünkü fiyat / alış
 * tarihindeki fiyat). Enflasyonda fiyat yerine TÜFE endeksi kullanılır.
 */
@Getter
@Setter
@NoArgsConstructor
public class PortfolioWhatIfResult {

    private UUID portfolioId;
    private String currency = "TRY";

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate asOf;

    /** Senaryolara dahil edilen pozisyonların toplam TL maliyeti (kıyas tabanı). */
    private BigDecimal totalCost;
    /** Gerçek portföyün bugünkü TL değeri (aynı pozisyon kümesi üzerinden). */
    private BigDecimal actualValue;
    /** Gerçek getiri yüzdesi. */
    private BigDecimal actualReturnPercent;

    /** Hesaba dahil edilen pozisyon sayısı (maliyet+tarih+değer olanlar). */
    private int includedHoldings;
    /** Veri eksikliğinden atlanan pozisyon sayısı. */
    private int skippedHoldings;

    /** Alternatif senaryolar: enflasyon, altın, dolar, mevduat. */
    private List<WhatIfScenario> scenarios;
}
