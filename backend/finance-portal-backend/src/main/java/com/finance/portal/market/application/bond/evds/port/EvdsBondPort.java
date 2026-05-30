package com.finance.portal.market.application.bond.evds.port;

import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;

import java.time.LocalDate;
import java.util.List;

public interface EvdsBondPort {

    /** Default data group ({@code bie_pydibs}) — DİBS Gösterge Değerleri. */
    List<EvdsSeriesInfo> fetchBondSeriesList();

    /**
     * Belirli bir EVDS data group'undan seri listesi (örn. {@code bie_pyks}
     * Kira Sertifikaları Gösterge Değerleri).
     */
    List<EvdsSeriesInfo> fetchSeriesList(String dataGroup);

    List<EvdsSeriesPoint> fetchIndicatorValues(String instrumentCode, LocalDate startDate, LocalDate endDate);

    List<EvdsSeriesPoint> fetchCouponRates(String instrumentCode, LocalDate startDate, LocalDate endDate);
}
