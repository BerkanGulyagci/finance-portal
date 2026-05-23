package com.finance.portal.portfolio.application.whatif;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * "Ne Olurdu?" senaryosu — yatırdığın parayı bu alternatife koysaydın bugünkü değeri.
 */
@Getter
@Setter
@NoArgsConstructor
public class WhatIfScenario {

    /** Makine anahtarı: inflation / gold / usd / deposit. */
    private String key;
    /** Görünür etiket (örn. "Enflasyon (TÜFE)"). */
    private String label;
    /** Bu senaryonun bugünkü TL değeri (toplam maliyet × faktör). Veri yoksa null. */
    private BigDecimal value;
    /** Toplam maliyete göre getiri yüzdesi. Veri yoksa null. */
    private BigDecimal returnPercent;
    /** Veri bulunabildi mi? false ise grafikte gösterilmez/soluk gösterilir. */
    private boolean available;
    /** Kısa not (örn. "Veri yok"). */
    private String note;

    public WhatIfScenario(String key, String label, BigDecimal value, BigDecimal returnPercent,
                          boolean available, String note) {
        this.key = key;
        this.label = label;
        this.value = value;
        this.returnPercent = returnPercent;
        this.available = available;
        this.note = note;
    }
}
