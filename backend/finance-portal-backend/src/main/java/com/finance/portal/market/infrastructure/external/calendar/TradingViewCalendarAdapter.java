package com.finance.portal.market.infrastructure.external.calendar;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link EconomicCalendarPort} için TradingView adaptörü.
 * Finnhub'ın yerini alır (ücretsiz planda ekonomik takvim kaldırıldı). API anahtarı gerektirmez,
 * Türkiye dahil 103 ülke kapsar. Dayanıklılık {@code EconomicCalendarService}'in LKG cache'i ile.
 */
@Component
public class TradingViewCalendarAdapter implements EconomicCalendarPort {

    private final TradingViewCalendarClient client;

    public TradingViewCalendarAdapter(TradingViewCalendarClient client) {
        this.client = client;
    }

    @Override
    public List<EconomicCalendarEvent> fetch(LocalDate from, LocalDate to) {
        return client.fetch(from, to);
    }
}
