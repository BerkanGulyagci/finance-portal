package com.finance.portal.market.application.economy.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Hesaplanmış tek bir ekonomik gösterge (son değer + değişimler).
 *
 * <p>Redis'e cache'lendiği için no-arg constructor + getter/setter ile
 * Jackson (de)serializasyonuna uygun POJO olarak tasarlanmıştır.
 */
@Getter
@Setter
@NoArgsConstructor
public class EconomyIndicator {

    private String key;
    private String label;
    private String category;
    private String categoryLabel;
    private String unit;
    private String frequency;
    private String seriesCode;

    /** Son (en güncel) değer. */
    private BigDecimal value;
    /** Son değerin dönemi (ham EVDS "Tarih": "2026-1", "2026-Q1", "2026", "22-05-2026"). */
    private String period;

    /** Bir önceki döneme ait değer. */
    private BigDecimal previousValue;
    /** Bir önceki döneme göre % değişim. */
    private BigDecimal changePercent;
    /** Bir önceki döneme göre mutlak (birim cinsinden) değişim. */
    private BigDecimal absoluteChange;
    /** Geçen yılın aynı dönemine göre % değişim (YoY) — anlamlı değilse null. */
    private BigDecimal yoyChangePercent;

    /** EVDS'den veri çekilebildi mi? false ise değer alanları null. */
    private boolean available;
    /** Negatif "akım" verisi mi (cari denge/bütçe)? Frontend % yerine mutlak değişim göstersin. */
    private boolean preferAbsolute;
}
