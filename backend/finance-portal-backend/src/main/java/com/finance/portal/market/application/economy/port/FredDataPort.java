package com.finance.portal.market.application.economy.port;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Küresel ekonomi serileri için veri kaynağı portu (FRED — Federal Reserve Economic Data).
 * Varsayılan implementasyon {@code FredEconomyAdapter}'dir.
 *
 * <p>EVDS portundan ({@code EconomyDataPort}) ayrı tutulur çünkü farklı sağlayıcı,
 * farklı kimlik doğrulama (API key query param) ve farklı yanıt formatı kullanır.
 */
public interface FredDataPort {

    /**
     * Verilen FRED seri kimliğinin belirtilen tarih aralığındaki noktalarını çeker.
     *
     * @param seriesId FRED seri kimliği (örn. {@code CPIAUCNS} — ABD TÜFE)
     * @param start    başlangıç tarihi (dahil)
     * @param end      bitiş tarihi (dahil)
     * @return unixTime'a göre artan sıralı noktalar; hata/anahtar yok durumunda boş liste
     */
    List<EconomySeriesPoint> fetchSeries(String seriesId, LocalDate start, LocalDate end);
}
