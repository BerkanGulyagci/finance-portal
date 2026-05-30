package com.finance.portal.market.application.calendar.model;

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

    public EconomicCalendarEvent() { }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }

    public Double getActual() { return actual; }
    public void setActual(Double actual) { this.actual = actual; }

    public Double getEstimate() { return estimate; }
    public void setEstimate(Double estimate) { this.estimate = estimate; }

    public Double getPrev() { return prev; }
    public void setPrev(Double prev) { this.prev = prev; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
