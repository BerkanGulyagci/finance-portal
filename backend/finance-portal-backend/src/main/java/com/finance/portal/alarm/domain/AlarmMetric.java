package com.finance.portal.alarm.domain;

/**
 * Alarmın izlediği metrik.
 * <ul>
 *   <li>{@code PRICE} — anlık fiyat (tüm enstrüman tipleri)</li>
 *   <li>{@code CHANGE_PERCENT} — günlük yüzde değişim</li>
 *   <li>{@code VOLUME} — işlem hacmi</li>
 *   <li>{@code MARGIN_RATIO} — VİOP başlangıç teminatına göre kalan özsermaye oranı (yalnız FUTURE).
 *       Decimal 0–1 aralığında saklanır (örn. 0.50 = %50). {@code direction=BELOW} + threshold=0.50
 *       semantiği: "teminat oranı %50'nin altına inerse alarm tetiklensin" (gerçek brokerda margin call).</li>
 * </ul>
 */
public enum AlarmMetric {
    PRICE,
    CHANGE_PERCENT,
    VOLUME,
    MARGIN_RATIO
}
