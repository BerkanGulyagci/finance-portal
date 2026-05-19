package com.finance.portal.market.infrastructure.external.precious;

import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BistMetalFiyatlariAdapter implements BistMetalFiyatlariPort {

    private final BistMetalFiyatlariClient bistMetalFiyatlariClient;

    public BistMetalFiyatlariAdapter(BistMetalFiyatlariClient bistMetalFiyatlariClient) {
        this.bistMetalFiyatlariClient = bistMetalFiyatlariClient;
    }

    @Override
    public List<BistMetalDailyPoint> fetchMetalPrices(PreciousMetalType metal, String startDate, String endDate) {
        return bistMetalFiyatlariClient.fetchMetalPrices(metal, startDate, endDate);
    }

    @Override
    public List<BistMetalDailyPoint> fetchMetalPricesLastDays(PreciousMetalType metal, int days) {
        return bistMetalFiyatlariClient.fetchMetalPricesLastDays(metal, days);
    }

    @Override
    public BistMetalDailyPoint fetchLatestValidPoint(PreciousMetalType metal) {
        return bistMetalFiyatlariClient.fetchLatestValidPoint(metal);
    }
}
