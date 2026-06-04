package com.finance.portal.market.application.economy.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Güncel kredi faiz oranları (yıllık %, TCMB EVDS akım verisi) — kredi taksit hesaplayıcısı için.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoanRates {

    private BigDecimal personal;    // İhtiyaç (TP.KTF10)
    private BigDecimal vehicle;     // Taşıt (TP.KTF11)
    private BigDecimal housing;     // Konut (TP.KTF12)
    private BigDecimal commercial;  // Ticari (TP.KTF17)
    private String period;          // EVDS son dönem (örn. "08-05-2026")
    private String source;
}
