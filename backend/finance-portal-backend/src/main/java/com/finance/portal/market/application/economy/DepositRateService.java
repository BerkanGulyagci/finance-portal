package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.DepositRates;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Güncel TL mevduat faiz oranları (vadeye göre, TCMB EVDS akım) + yıllık enflasyon —
 * mevduat getiri hesaplayıcısı için.
 */
@Service
public class DepositRateService {

    private static final Logger log = LoggerFactory.getLogger(DepositRateService.class);
    private static final String SOURCE = "TCMB EVDS";

    private static final String CODE_1M = "TP.TRY.MT01";   // 1 aya kadar
    private static final String CODE_3M = "TP.TRY.MT02";   // 3 aya kadar
    private static final String CODE_6M = "TP.TRY.MT03";   // 6 aya kadar
    private static final String CODE_1Y = "TP.TRY.MT04";   // 1 yıla kadar

    private final EconomyDataPort economyDataPort;
    private final EconomyService economyService;

    // Stopaj kademeleri config'den (mevzuat değişince application.yml / env'den güncellenir)
    @Value("${deposit.stopaj.up-to-6m:10}")
    private BigDecimal stopaj6m;
    @Value("${deposit.stopaj.up-to-1y:7.5}")
    private BigDecimal stopaj1y;
    @Value("${deposit.stopaj.over-1y:5}")
    private BigDecimal stopajOver1y;

    public DepositRateService(EconomyDataPort economyDataPort, EconomyService economyService) {
        this.economyDataPort = economyDataPort;
        this.economyService = economyService;
    }

    @Cacheable(cacheNames = "market.economy", key = "'depositrates'")
    public DepositRates getDepositRates() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(3);

        EconomySeriesPoint m1 = latest(CODE_1M, start, end);
        EconomySeriesPoint m3 = latest(CODE_3M, start, end);
        EconomySeriesPoint m6 = latest(CODE_6M, start, end);
        EconomySeriesPoint m12 = latest(CODE_1Y, start, end);

        DepositRates r = new DepositRates();
        r.setUpTo1Month(m1 != null ? m1.getValue() : null);
        r.setUpTo3Months(m3 != null ? m3.getValue() : null);
        r.setUpTo6Months(m6 != null ? m6.getValue() : null);
        r.setUpTo1Year(m12 != null ? m12.getValue() : null);
        r.setPeriod(m3 != null ? m3.getPeriod() : null);
        r.setInflationYoy(tufeYoy());
        r.setStopaj6m(stopaj6m);
        r.setStopaj1y(stopaj1y);
        r.setStopajOver1y(stopajOver1y);
        r.setSource(SOURCE);

        log.info("[DepositRate] 1ay={} 3ay={} 6ay={} 1yıl={} enflasyon={}",
                r.getUpTo1Month(), r.getUpTo3Months(), r.getUpTo6Months(), r.getUpTo1Year(), r.getInflationYoy());
        return r;
    }

    private EconomySeriesPoint latest(String code, LocalDate start, LocalDate end) {
        List<EconomySeriesPoint> pts = economyDataPort.fetchSeries(code, start, end);
        return pts.isEmpty() ? null : pts.get(pts.size() - 1);
    }

    /** Güncel TÜFE yıllık enflasyonu (özet servisinden). */
    private BigDecimal tufeYoy() {
        try {
            for (EconomyIndicator i : economyService.getSummary()) {
                if ("tufe".equals(i.getKey())) return i.getYoyChangePercent();
            }
        } catch (Exception e) {
            log.debug("[DepositRate] enflasyon alınamadı: {}", e.getMessage());
        }
        return null;
    }
}
