package com.finance.portal.market.infrastructure.external.calendar;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link EconomicCalendarPort} için Finnhub adaptörü.
 */
@Component
public class FinnhubCalendarAdapter implements EconomicCalendarPort {

    private final FinnhubCalendarClient client;

    public FinnhubCalendarAdapter(FinnhubCalendarClient client) {
        this.client = client;
    }

    @Override
    public List<EconomicCalendarEvent> fetch(LocalDate from, LocalDate to) {
        return client.fetch(from, to);
    }
}
