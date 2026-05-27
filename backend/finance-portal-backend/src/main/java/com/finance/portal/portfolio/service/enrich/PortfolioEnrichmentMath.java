package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Portföy pozisyon zenginleştirmesi için ortak sayısal yardımcılar.
 * Asset-type başına ayrı *HoldingEnricher sınıflarına bölünmüş olan
 * {@link com.finance.portal.portfolio.service.PortfolioHoldingMarketEnricher}
 * orkestratörü ve alt enricher'ların ortak ihtiyacı (scale sabitleri + MA uygulayıcı).
 */
public final class PortfolioEnrichmentMath {

    /** Fiyat kayıt scale'i (BIST/COINGECKO/FX gibi 8 ondalıklı veri kaynakları için). */
    public static final int PRICE_SCALE = 8;

    /** Para birimi miktar/değer scale'i (marketValue, profitLoss; 4 ondalık yeterli). */
    public static final int MONEY_SCALE = 4;

    /** Fon birim payı ve fon-bazlı maliyet hesabı için ekstra hassasiyet. */
    public static final int FUND_MONEY_SCALE = 8;

    private PortfolioEnrichmentMath() {
        // utility
    }

    /**
     * Verilen kapanış listesi üzerinden MA20 ve MA50 hesaplar ve holding'e uygular.
     * Liste null/boş ise no-op; tek tek MA değerleri null dönerse ilgili alan dokunulmaz.
     */
    public static void applyMasFromCloses(PortfolioHoldingResponse holding, List<BigDecimal> closes) {
        if (closes == null || closes.isEmpty()) {
            return;
        }
        BigDecimal ma20 = PortfolioMovingAverage.simpleMa(closes, 20);
        BigDecimal ma50 = PortfolioMovingAverage.simpleMa(closes, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    /** marketValue = price × qty, MONEY_SCALE'e yuvarlanmış (HALF_UP). */
    public static BigDecimal marketValue(BigDecimal price, BigDecimal qty) {
        if (price == null || qty == null) {
            return null;
        }
        return price.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** profitLoss = marketValue − totalCost, MONEY_SCALE'e yuvarlanmış (HALF_UP). */
    public static BigDecimal profitLoss(BigDecimal marketValue, BigDecimal totalCost) {
        if (marketValue == null || totalCost == null) {
            return null;
        }
        return marketValue.subtract(totalCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
