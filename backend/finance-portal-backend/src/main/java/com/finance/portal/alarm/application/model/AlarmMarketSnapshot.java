package com.finance.portal.alarm.application.model;

import com.finance.portal.alarm.domain.AlarmMetric;

import java.math.BigDecimal;

/**
 * Bir enstrümanın alarm değerlendirmesi için anlık metrik değerleri.
 * Her metrik bazı tiplerde bulunmayabilir → {@code null} olabilir.
 *
 * @param price          anlık fiyat (TRY/USD vb. enstrümanın doğal birimi)
 * @param changePercent  günlük yüzde değişim
 * @param volume         işlem hacmi
 * @param marginRatio    VİOP teminat oranı (equity / initialMargin). Kullanıcıya bağlı olduğundan
 *                       jenerik probe'tan üretilmez — MarginAlarmEvaluator ayrıca doldurur (yalnız FUTURE).
 */
public record AlarmMarketSnapshot(BigDecimal price, BigDecimal changePercent, BigDecimal volume,
                                  BigDecimal marginRatio) {

    /** Geriye uyumlu üçlü ctor — yeni alanlar null. Mevcut adapter çağrıları kırılmaz. */
    public AlarmMarketSnapshot(BigDecimal price, BigDecimal changePercent, BigDecimal volume) {
        this(price, changePercent, volume, null);
    }

    /** Verilen metriğe karşılık gelen değeri döner (yoksa {@code null}). */
    public BigDecimal valueFor(AlarmMetric metric) {
        if (metric == null) {
            return null;
        }
        return switch (metric) {
            case PRICE -> price;
            case CHANGE_PERCENT -> changePercent;
            case VOLUME -> volume;
            case MARGIN_RATIO -> marginRatio;
        };
    }
}
