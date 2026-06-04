package com.finance.portal.market.application.calendar.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Tek bir ekonomik takvim olayı.
 * Finnhub /calendar/economic alanlarına 1:1 karşılık gelir.
 *
 * <ul>
 *   <li>{@code time} — "yyyy-MM-dd HH:mm:ss" formatında UTC; tüm-gün olaylar için saat 00:00:00.</li>
 *   <li>{@code country} — ISO 3166-1 alpha-2 (örn. "US", "TR", "EU").</li>
 *   <li>{@code currency} — country'den türetilmiş ISO 4217 (sunucu tarafı dönüştürür; null olabilir).</li>
 *   <li>{@code impact} — "low" | "medium" | "high"; tatil olayları için "holiday".</li>
 *   <li>{@code actual}/{@code estimate}/{@code prev} — sayısal değerler (null = bilinmiyor).</li>
 *   <li>{@code unit} — değer birimi (örn. "%", "USD", "EUR").</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class EconomicCalendarEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String time;
    private String country;
    private String currency;
    private String event;
    private String impact;
    private Double actual;
    private Double estimate;
    private Double prev;
    private String unit;
}
