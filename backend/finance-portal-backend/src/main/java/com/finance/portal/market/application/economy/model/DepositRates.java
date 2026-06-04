package com.finance.portal.market.application.economy.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Güncel TL mevduat faiz oranları (vadeye göre, yıllık %) + güncel yıllık enflasyon —
 * mevduat getiri hesaplayıcısı için (reel getiri dahil).
 */
@Getter
@Setter
@NoArgsConstructor
public class DepositRates {

    private BigDecimal upTo1Month;   // TP.TRY.MT01
    private BigDecimal upTo3Months;  // TP.TRY.MT02
    private BigDecimal upTo6Months;  // TP.TRY.MT03
    private BigDecimal upTo1Year;    // TP.TRY.MT04
    private BigDecimal inflationYoy; // TÜFE yıllık (reel getiri için)
    // Stopaj kademeleri (yıllık %, config'den) — canlı değil; mevzuat değişince application.yml'den güncellenir
    private BigDecimal stopaj6m;     // 6 aya kadar
    private BigDecimal stopaj1y;     // 6 ay – 1 yıl
    private BigDecimal stopajOver1y; // 1 yıldan uzun
    private String period;
    private String source;
}
