package com.finance.portal.portfolio.application.whatif;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * "Ne Olurdu?" zaman serisinde tek bir tarih: yatırılan paranın o gün her senaryodaki TL değeri.
 * Bir senaryonun verisi yoksa o alan null (frontend o çizgiyi atlar).
 */
@Getter
@Setter
@NoArgsConstructor
public class WhatIfSeriesPoint {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** O ana kadar yatırılmış toplam maliyet (buyDate ≤ t olan pozisyonların TL maliyeti) — % normalize için. */
    private BigDecimal cost;
    /** Gerçek (varlığın kendi fiyatıyla) bugünkü TL değeri. */
    private BigDecimal actual;
    private BigDecimal inflation;
    /** ABD enflasyonu (USD alım gücü, TL cinsinden) = ABD CPI oranı × USD/TRY oranı. */
    private BigDecimal usInflation;
    private BigDecimal gold;
    private BigDecimal usd;
    private BigDecimal deposit;
    // Portföy dışı hazır benchmark'lar (TL bazında)
    private BigDecimal bist100;
    private BigDecimal bitcoin;
    private BigDecimal sp500;

    /** Kullanıcının eklediği serbest kıyaslar: id ("ASSETTYPE|SYMBOL") → o tarihteki TL değer. */
    private Map<String, BigDecimal> extra;

    public WhatIfSeriesPoint(LocalDate date) {
        this.date = date;
    }
}
