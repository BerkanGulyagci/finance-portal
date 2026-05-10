package com.finance.portal.market.application.gold;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Tarihsel altın fiyat noktası.
 *
 * BIST kaynağında:
 *   close            = gramCloseTry (kapanış TL/gram)
 *   open             = önceki günün close'u (sentetik — BIST open vermez)
 *   high             = gramHighTry
 *   low              = gramLowTry
 *   weightedAverage  = gramWeightedAverageTry (ana referans / officialPureGoldGramTry)
 *
 * Yahoo fallback'te:
 *   close/open/high/low = USD cinsinden ONS fiyatı
 */
@Data
@NoArgsConstructor
public class GoldHistoryPoint implements Serializable {

    private String date;

    /** Kapanış fiyatı (gram TL veya ONS USD) */
    private BigDecimal close;

    /**
     * Açılış fiyatı.
     * BIST: önceki günün close'u (sentetik).
     * Yahoo: gerçek open.
     */
    private BigDecimal open;

    /** Günlük en yüksek */
    private BigDecimal high;

    /** Günlük en düşük */
    private BigDecimal low;

    /** Hacim */
    private Long volume;

    // ── BIST'e özgü ek alanlar ────────────────────────────────────────────────

    /** Ağırlıklı ortalama gram TL (officialPureGoldGramTry referansı) — TRY modu */
    private BigDecimal weightedAverage;

    /** Ham BIST TL/Kg kapanış — TRY modu */
    private BigDecimal closeTryKg;

    /** Ham BIST TL/Kg ağırlıklı ortalama — TRY modu */
    private BigDecimal weightedAverageTryKg;

    // ── ONS/USD alanları — USD modu ───────────────────────────────────────────

    /** Ağırlıklı ortalama USD/Ons — USD modu */
    private BigDecimal weightedAverageUsdOns;

    /** İşlem hacmi USD — USD modu */
    private BigDecimal volumeUsd;

    /** İşlem miktarı Kg — USD modu */
    private BigDecimal quantityKg;

    public GoldHistoryPoint(String date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }
}
