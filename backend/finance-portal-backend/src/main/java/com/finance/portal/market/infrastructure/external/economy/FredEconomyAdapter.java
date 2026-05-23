package com.finance.portal.market.infrastructure.external.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link FredDataPort} için FRED (St. Louis Fed) adaptörü.
 */
@Component
public class FredEconomyAdapter implements FredDataPort {

    private final FredEconomyClient client;

    public FredEconomyAdapter(FredEconomyClient client) {
        this.client = client;
    }

    @Override
    public List<EconomySeriesPoint> fetchSeries(String seriesId, LocalDate start, LocalDate end) {
        return client.fetchSeries(seriesId, start, end);
    }
}
