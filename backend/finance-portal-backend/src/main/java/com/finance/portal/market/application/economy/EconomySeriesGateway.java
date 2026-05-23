package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Gösterge serisini doğru sağlayıcıdan çeken yönlendirici.
 *
 * <p>{@link EconomyIndicatorDef#getSource()} alanına göre seriyi TCMB EVDS
 * ({@link EconomyDataPort}) veya FRED ({@link FredDataPort}) üzerinden getirir.
 * Böylece {@code EconomyService} ve {@code EconomyChartService} kaynaktan
 * habersiz kalır — tek bir {@link #fetch} çağrısı yeterlidir.
 */
@Component
public class EconomySeriesGateway {

    private final EconomyDataPort evdsPort;
    private final FredDataPort fredPort;

    public EconomySeriesGateway(EconomyDataPort evdsPort, FredDataPort fredPort) {
        this.evdsPort = evdsPort;
        this.fredPort = fredPort;
    }

    /** Göstergenin kaynağına göre seriyi ilgili porttan çeker. */
    public List<EconomySeriesPoint> fetch(EconomyIndicatorDef def, LocalDate start, LocalDate end) {
        return def.getSource() == EconomyIndicatorDef.Source.FRED
                ? fredPort.fetchSeries(def.getSeriesCode(), start, end)
                : evdsPort.fetchSeries(def.getSeriesCode(), start, end);
    }
}
