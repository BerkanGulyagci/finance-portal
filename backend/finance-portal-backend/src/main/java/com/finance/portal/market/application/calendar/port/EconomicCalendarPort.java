package com.finance.portal.market.application.calendar.port;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * Ekonomik takvim olaylarını dış sağlayıcıdan çekmek için port.
 * Implementasyonlar (örn. Finnhub) hata durumunda boş liste döner; istisna fırlatmaz.
 */
public interface EconomicCalendarPort {

    /**
     * Verilen tarih aralığındaki ekonomik takvim olaylarını döndürür.
     *
     * @param from kapsayıcı başlangıç tarihi (UTC)
     * @param to   kapsayıcı bitiş tarihi (UTC)
     * @return olaylar (zamana göre artan sırada); sağlayıcı boş/hata → boş liste
     */
    List<EconomicCalendarEvent> fetch(LocalDate from, LocalDate to);
}
