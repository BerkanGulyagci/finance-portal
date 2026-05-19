package com.finance.portal.market.application.bond.evds.port;

import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;

import java.time.LocalDate;
import java.util.List;

public interface EvdsBondPort {

    List<EvdsSeriesInfo> fetchBondSeriesList();

    List<EvdsSeriesPoint> fetchIndicatorValues(String instrumentCode, LocalDate startDate, LocalDate endDate);

    List<EvdsSeriesPoint> fetchCouponRates(String instrumentCode, LocalDate startDate, LocalDate endDate);
}
