package com.finance.portal.portfolio.application.viop.valuation;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VİOP pozisyonu için saf matematik: notional, teminat, kaldıraç, kâr/zarar.
 * Stateless, hiçbir external dependency yok — birim test trivial.
 *
 * <p><b>Formüller (yön LONG=+1, SHORT=−1):</b>
 * <pre>
 *   notional       = qty × price × multiplier
 *   marginPosted   = qty × avgEntryPrice × multiplier × marginRate
 *   pnl            = qty × (currentPrice − avgEntryPrice) × multiplier × directionSign
 *   leverage       = notional / marginPosted
 *   dailyPnl       = qty × (settlement − prevSettlement) × multiplier × directionSign
 * </pre>
 *
 * <p>Portföy toplamına {@code marginPosted + pnl} katılır (notional değil — kaldıraçlı
 * pozisyon küçük portföyü 8-10x şişirir, yanlış yönlendirici).
 */
@Service
public class ViopValuationService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;

    /** Yön çarpanı: LONG=+1, SHORT=−1. */
    public BigDecimal directionSign(String direction) {
        if (direction == null) return BigDecimal.ONE; // backward-compat: LONG default
        return "SHORT".equalsIgnoreCase(direction.trim()) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
    }

    /**
     * Nominal pozisyon büyüklüğü (piyasada kontrol edilen tutar).
     * Risk göstergesi — portföy toplamına eklenmez.
     */
    public BigDecimal notional(BigDecimal qty, BigDecimal currentPrice, ViopContractSpec spec) {
        if (anyNull(qty, currentPrice, spec)) return BigDecimal.ZERO;
        return qty.multiply(currentPrice).multiply(spec.multiplier())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Başlangıç teminatı — pozisyon açarken hesaba bloke edilen TL (veya kontrat parası).
     * Cüzdandan gerçekten çıkan tutar.
     */
    public BigDecimal marginPosted(BigDecimal qty, BigDecimal avgEntryPrice, ViopContractSpec spec) {
        if (anyNull(qty, avgEntryPrice, spec)) return BigDecimal.ZERO;
        return qty.multiply(avgEntryPrice).multiply(spec.multiplier()).multiply(spec.marginRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Kâr/zarar = fiyat farkı × çarpan × adet × yön. Long için yükselişte +, düşüşte −.
     * Short tam tersi.
     */
    public BigDecimal pnl(BigDecimal qty, BigDecimal avgEntryPrice, BigDecimal currentPrice,
                          ViopContractSpec spec, String direction) {
        if (anyNull(qty, avgEntryPrice, currentPrice, spec)) return BigDecimal.ZERO;
        return currentPrice.subtract(avgEntryPrice)
                .multiply(qty)
                .multiply(spec.multiplier())
                .multiply(directionSign(direction))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Günlük (mark-to-market) kâr/zarar — uzlaşma fiyatı kullanır.
     * VİOP'ta günlük netleşmiş nakit akışı.
     */
    public BigDecimal dailyPnl(BigDecimal qty, BigDecimal currentSettlement, BigDecimal prevSettlement,
                               ViopContractSpec spec, String direction) {
        if (anyNull(qty, currentSettlement, prevSettlement, spec)) return BigDecimal.ZERO;
        return currentSettlement.subtract(prevSettlement)
                .multiply(qty)
                .multiply(spec.multiplier())
                .multiply(directionSign(direction))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Kaldıraç = notional / margin. Risk göstergesi (1₺ teminat ile X₺ pozisyon).
     */
    public BigDecimal leverage(BigDecimal notional, BigDecimal margin) {
        if (notional == null || margin == null || margin.signum() == 0) return BigDecimal.ZERO;
        return notional.divide(margin, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Portföy toplamına katılan "market value": gerçek bağlı sermaye + biriken kâr/zarar.
     * KAYITLI tutar (notional değil) — pasta grafiği bozmaz, dönem getirisi doğru.
     */
    public BigDecimal portfolioContribution(BigDecimal marginPosted, BigDecimal pnl) {
        if (marginPosted == null) marginPosted = BigDecimal.ZERO;
        if (pnl == null) pnl = BigDecimal.ZERO;
        return marginPosted.add(pnl).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean anyNull(Object... os) {
        for (Object o : os) if (o == null) return true;
        return false;
    }
}
