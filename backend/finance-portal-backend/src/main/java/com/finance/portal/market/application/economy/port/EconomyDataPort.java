package com.finance.portal.market.application.economy.port;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Türkiye ekonomisi göstergeleri için veri kaynağı portu.
 * Varsayılan implementasyon TCMB EVDS'tir ({@code EvdsEconomyAdapter}).
 */
public interface EconomyDataPort {

    /**
     * Verilen EVDS seri kodunun belirtilen tarih aralığındaki noktalarını çeker.
     *
     * @param seriesCode EVDS seri kodu (örn. {@code TP.FG.J0})
     * @param start      başlangıç tarihi (dahil)
     * @param end        bitiş tarihi (dahil)
     * @return unixTime'a göre artan sıralı noktalar; hata durumunda boş liste
     */
    List<EconomySeriesPoint> fetchSeries(String seriesCode, LocalDate start, LocalDate end);
}
