package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.model.LoanRates;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Güncel kredi faiz oranları (TCMB EVDS akım, yıllık %) — kredi taksit hesaplayıcısı için.
 * İhtiyaç/Taşıt/Konut/Ticari kredi faizlerinin en güncel değerini döndürür.
 */
@Service
public class LoanRateService {

    private static final Logger log = LoggerFactory.getLogger(LoanRateService.class);
    private static final String SOURCE = "TCMB EVDS";

    private static final String CODE_PERSONAL = "TP.KTF10";   // İhtiyaç
    private static final String CODE_VEHICLE = "TP.KTF11";    // Taşıt
    private static final String CODE_HOUSING = "TP.KTF12";    // Konut
    private static final String CODE_COMMERCIAL = "TP.KTF17"; // Ticari

    private final EconomyDataPort economyDataPort;

    public LoanRateService(EconomyDataPort economyDataPort) {
        this.economyDataPort = economyDataPort;
    }

    @Cacheable(cacheNames = "market.economy", key = "'loanrates'")
    public LoanRates getLoanRates() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(3); // haftalık seri — son birkaç hafta yeterli

        EconomySeriesPoint personal = latest(CODE_PERSONAL, start, end);
        EconomySeriesPoint vehicle = latest(CODE_VEHICLE, start, end);
        EconomySeriesPoint housing = latest(CODE_HOUSING, start, end);
        EconomySeriesPoint commercial = latest(CODE_COMMERCIAL, start, end);

        LoanRates r = new LoanRates();
        r.setPersonal(personal != null ? personal.getValue() : null);
        r.setVehicle(vehicle != null ? vehicle.getValue() : null);
        r.setHousing(housing != null ? housing.getValue() : null);
        r.setCommercial(commercial != null ? commercial.getValue() : null);
        r.setPeriod(personal != null ? personal.getPeriod() : null);
        r.setSource(SOURCE);

        log.info("[LoanRate] ihtiyaç={} taşıt={} konut={} ticari={}",
                r.getPersonal(), r.getVehicle(), r.getHousing(), r.getCommercial());
        return r;
    }

    private EconomySeriesPoint latest(String code, LocalDate start, LocalDate end) {
        List<EconomySeriesPoint> pts = economyDataPort.fetchSeries(code, start, end);
        return pts.isEmpty() ? null : pts.get(pts.size() - 1);
    }
}
