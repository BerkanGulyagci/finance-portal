package com.finance.portal.market.infrastructure.external.bond;

import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class EvdsBondAdapter implements EvdsBondPort {

    private final EvdsBondClient evdsBondClient;

    public EvdsBondAdapter(EvdsBondClient evdsBondClient) {
        this.evdsBondClient = evdsBondClient;
    }

    @Override
    public List<EvdsSeriesInfo> fetchBondSeriesList() {
        return evdsBondClient.fetchBondSeriesList();
    }

    @Override
    public List<EvdsSeriesInfo> fetchSeriesList(String dataGroup) {
        return evdsBondClient.fetchSeriesList(dataGroup);
    }

    @Override
    public List<EvdsSeriesPoint> fetchIndicatorValues(String instrumentCode, LocalDate startDate, LocalDate endDate) {
        return evdsBondClient.fetchIndicatorValues(instrumentCode, startDate, endDate);
    }

    @Override
    public List<EvdsSeriesPoint> fetchCouponRates(String instrumentCode, LocalDate startDate, LocalDate endDate) {
        return evdsBondClient.fetchCouponRates(instrumentCode, startDate, endDate);
    }
}
