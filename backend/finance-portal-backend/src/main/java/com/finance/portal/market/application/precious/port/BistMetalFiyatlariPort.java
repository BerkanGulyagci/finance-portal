package com.finance.portal.market.application.precious.port;

import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;

import java.util.List;

public interface BistMetalFiyatlariPort {

    List<BistMetalDailyPoint> fetchMetalPrices(PreciousMetalType metal, String startDate, String endDate);

    List<BistMetalDailyPoint> fetchMetalPricesLastDays(PreciousMetalType metal, int days);

    BistMetalDailyPoint fetchLatestValidPoint(PreciousMetalType metal);
}
