package com.finance.portal.market.infrastructure.external.precious;

import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
import com.finance.portal.market.application.precious.port.BistPreciousMetalsPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BistPreciousMetalsAdapter implements BistPreciousMetalsPort {

    private final BistPreciousMetalsClient bistPreciousMetalsClient;

    public BistPreciousMetalsAdapter(BistPreciousMetalsClient bistPreciousMetalsClient) {
        this.bistPreciousMetalsClient = bistPreciousMetalsClient;
    }

    @Override
    public List<BistPreciousMetalPoint> fetchHistory(PreciousMetalType metal, PriceUnit unit, String startDate, String endDate) {
        return bistPreciousMetalsClient.fetchHistory(metal, unit, startDate, endDate);
    }

    @Override
    public List<BistPreciousMetalPoint> fetchHistoryLastDays(PreciousMetalType metal, PriceUnit unit, int days) {
        return bistPreciousMetalsClient.fetchHistoryLastDays(metal, unit, days);
    }

    @Override
    public BistPreciousMetalPoint fetchLatestValidPoint(PreciousMetalType metal, PriceUnit unit) {
        return bistPreciousMetalsClient.fetchLatestValidPoint(metal, unit);
    }
}
