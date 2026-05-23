package com.finance.portal.market.application.indicator;

import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Piyasa Özeti widget'ı için sade gösterge seti.
 *
 * <p>Değerler TCMB EVDS'ten ({@link EconomyService}) canlı gelir; veri çekilemezse
 * en son bilinen değere düşülür (fallback). Eskiden HTML scraping + sabit değer kullanılıyordu.
 */
@Service
public class EconomicIndicatorService {

    // EVDS erişilemezse en son bilinen değerler (manuel güncellenebilir)
    private static final String FALLBACK_POLICY_RATE = "40";
    private static final String FALLBACK_INFLATION = "32.37";

    private final EconomyService economyService;

    public EconomicIndicatorService(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Cacheable(cacheNames = "market.indicators", key = "'all'")
    public Map<String, String> getIndicators() {
        List<EconomyIndicator> summary = economyService.getSummary();

        Map<String, String> result = new LinkedHashMap<>();
        // Politika faizi — son değer (örn. 40)
        result.put("policyRate", str(value(summary, "politikaFaizi"), FALLBACK_POLICY_RATE));
        // TÜFE — yıllık enflasyon (YoY %)
        result.put("inflation", str(yoy(summary, "tufe"), FALLBACK_INFLATION));
        // Yİ-ÜFE — üretici enflasyonu (YoY %)
        result.put("ppi", str(yoy(summary, "ufe"), null));
        // TL mevduat faizi — son değer
        result.put("depositRate", str(value(summary, "mevduatFaizi"), null));
        return result;
    }

    private BigDecimal value(List<EconomyIndicator> list, String key) {
        for (EconomyIndicator i : list) {
            if (key.equals(i.getKey())) return i.getValue();
        }
        return null;
    }

    private BigDecimal yoy(List<EconomyIndicator> list, String key) {
        for (EconomyIndicator i : list) {
            if (key.equals(i.getKey())) return i.getYoyChangePercent();
        }
        return null;
    }

    /** BigDecimal'ı sondaki sıfırları atılmış sade string'e çevirir; null ise fallback. */
    private String str(BigDecimal v, String fallback) {
        if (v == null) return fallback;
        return v.stripTrailingZeros().toPlainString();
    }
}
