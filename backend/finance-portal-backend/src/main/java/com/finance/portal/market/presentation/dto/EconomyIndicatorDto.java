package com.finance.portal.market.presentation.dto;

import java.math.BigDecimal;

/**
 * Tek bir ekonomik gösterge — özet panel yanıtı.
 *
 * @param key              gösterge anahtarı (örn. {@code tufe})
 * @param label            görünen ad (örn. "TÜFE — Tüketici Enflasyonu")
 * @param unit             birim (örn. {@code %}, {@code endeks}, {@code milyon USD})
 * @param frequency        veri frekansı ({@code DAILY|WEEKLY|MONTHLY|QUARTERLY|YEARLY})
 * @param value            son değer
 * @param period           son değerin dönemi (ham EVDS, örn. {@code 2026-1})
 * @param previousValue    bir önceki dönemin değeri
 * @param changePercent    bir önceki döneme göre % değişim
 * @param absoluteChange   bir önceki döneme göre mutlak (birim cinsinden) değişim
 * @param yoyChangePercent geçen yılın aynı dönemine göre % değişim (YoY); yoksa null
 * @param available        EVDS'ten veri çekilebildi mi
 * @param preferAbsolute   negatif "akım" verisi mi (cari denge/bütçe) — frontend % yerine mutlak değişim göstermeli
 * @param seriesCode       kaynak EVDS seri kodu
 */
public record EconomyIndicatorDto(
        String key,
        String label,
        String unit,
        String frequency,
        BigDecimal value,
        String period,
        BigDecimal previousValue,
        BigDecimal changePercent,
        BigDecimal absoluteChange,
        BigDecimal yoyChangePercent,
        boolean available,
        boolean preferAbsolute,
        String seriesCode
) {
}
