package com.finance.portal.market.application.calendar;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Ekonomik takvim verilerini sağlar (Finnhub backed).
 * Cache: {@code market.calendar} — TTL CacheConfig'de tanımlı (varsayılan 1 saat).
 */
@Service
public class EconomicCalendarService {

    private static final Logger log = LoggerFactory.getLogger(EconomicCalendarService.class);

    /** Tek istekte istenebilecek maksimum aralık (gün). Daha büyük aralık kırpılır. */
    private static final long MAX_RANGE_DAYS = 400;

    private final EconomicCalendarPort port;

    public EconomicCalendarService(EconomicCalendarPort port) {
        this.port = port;
    }

    /**
     * Verilen aralıktaki tüm olayları döner.
     * {@code from} {@code to}'dan büyükse swap edilir; aralık {@link #MAX_RANGE_DAYS} ile sınırlanır.
     */
    @Cacheable(cacheNames = "market.calendar", key = "#from.toString() + '_' + #to.toString()")
    public List<EconomicCalendarEvent> getEvents(LocalDate from, LocalDate to) {
        LocalDate start = from;
        LocalDate end = to;
        if (start.isAfter(end)) {
            LocalDate tmp = start; start = end; end = tmp;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_RANGE_DAYS) {
            log.warn("[Calendar] Aralık {} gün — {} güne kırpıldı (from={}, to={})",
                    days, MAX_RANGE_DAYS, start, end);
            end = start.plusDays(MAX_RANGE_DAYS);
        }
        return port.fetch(start, end);
    }
}
