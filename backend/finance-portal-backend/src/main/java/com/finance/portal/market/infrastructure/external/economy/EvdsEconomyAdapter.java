package com.finance.portal.market.infrastructure.external.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link EconomyDataPort} için TCMB EVDS adaptörü.
 */
@Component
public class EvdsEconomyAdapter implements EconomyDataPort {

    private final EvdsEconomyClient client;

    public EvdsEconomyAdapter(EvdsEconomyClient client) {
        this.client = client;
    }

    @Override
    public List<EconomySeriesPoint> fetchSeries(String seriesCode, LocalDate start, LocalDate end) {
        return client.fetchSeries(seriesCode, start, end);
    }
}
