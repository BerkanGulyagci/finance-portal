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
 */
public record AlarmMarketSnapshot(BigDecimal price, BigDecimal changePercent, BigDecimal volume) {

    /** Verilen metriğe karşılık gelen değeri döner (yoksa {@code null}). */
    public BigDecimal valueFor(AlarmMetric metric) {
        if (metric == null) {
            return null;
        }
        return switch (metric) {
            case PRICE -> price;
            case CHANGE_PERCENT -> changePercent;
            case VOLUME -> volume;
        };
    }
}
