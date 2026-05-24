package com.finance.portal.alarm.domain;

/**
 * Alarmın izlediği metrik.
 * <ul>
 *   <li>{@code PRICE} — anlık fiyat (tüm enstrüman tipleri)</li>
 *   <li>{@code CHANGE_PERCENT} — günlük yüzde değişim</li>
 *   <li>{@code VOLUME} — işlem hacmi</li>
 * </ul>
 */
public enum AlarmMetric {
    PRICE,
    CHANGE_PERCENT,
    VOLUME
}
