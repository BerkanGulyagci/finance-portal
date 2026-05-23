package com.finance.portal.portfolio.service;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Varlık tutarlarını portföy para birimine (TL) çevirir.
 *
 * <p>Portföy toplamları (piyasa değeri, K/Z, maliyet) farklı para birimli varlıkları
 * (örn. USD yabancı hisse + TL varlıklar) içerebilir. Bunlar dönüştürülmeden toplanırsa
 * boyut olarak yanlış olur (USD sayı TL'ye eklenir). Bu yardımcı her tutarı güncel TCMB
 * satış kuruyla TL'ye çevirir.
 *
 * <p>Basitleştirme: maliyet de güncel kurla çevrilir (alış anındaki kur saklanmıyor),
 * yani saf "kur kârı/zararı" ayrıştırılmaz; ancak toplamlar boyut olarak doğru (hep TL) olur.
 */
@Component
public class PortfolioCurrencyConverter {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCurrencyConverter.class);

    private final MarketFxService fxService;

    public PortfolioCurrencyConverter(MarketFxService fxService) {
        this.fxService = fxService;
    }

    /**
     * {@code amount} ({@code currency} cinsinden) → TL. TL/boş para birimi aynen döner.
     * Kur bulunamazsa (egzotik para birimi) tutar olduğu gibi döner (en iyi çaba).
     */
    public BigDecimal toTry(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        BigDecimal rate = rateToTry(currency);
        return rate == null ? amount : amount.multiply(rate);
    }

    /** 1 birim {@code currency} kaç TL eder? TL/boş → 1; bilinmiyorsa null. */
    public BigDecimal rateToTry(String currency) {
        if (currency == null || currency.isBlank()) {
            return BigDecimal.ONE;
        }
        String cur = currency.trim().toUpperCase(Locale.ROOT);
        if ("TRY".equals(cur) || "TL".equals(cur)) {
            return BigDecimal.ONE;
        }
        try {
            FxLatestRates latest = fxService.getTcmbLatestRates(cur);
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            FxRateItem rate = latest.getRates().stream()
                    .filter(r -> cur.equalsIgnoreCase(r.getSymbol()))
                    .findFirst()
                    .orElse(null);
            if (rate == null) {
                return null;
            }
            BigDecimal price = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (price == null) {
                return null;
            }
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1 ? price.divide(BigDecimal.valueOf(unit), 8, java.math.RoundingMode.HALF_UP) : price;
        } catch (Exception e) {
            log.debug("FX kuru alınamadı ({} → TRY): {}", cur, e.getMessage());
            return null;
        }
    }
}
