package com.finance.portal.market.application.precious.port;

import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;

import java.util.List;

public interface BistPreciousMetalsPort {

    List<BistPreciousMetalPoint> fetchHistory(PreciousMetalType metal, PriceUnit unit, String startDate, String endDate);

    List<BistPreciousMetalPoint> fetchHistoryLastDays(PreciousMetalType metal, PriceUnit unit, int days);

    BistPreciousMetalPoint fetchLatestValidPoint(PreciousMetalType metal, PriceUnit unit);
}
