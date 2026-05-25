package com.finance.portal.market.application.bond.eurobond.model;

/** HMB dış borç tahvil listesi künyesi (statik; BI'dan bağımsız fallback verisi). */
public record HmbBond(
        String isin,
        String currency,      // USD / EUR / JPY
        String couponRate,    // "5.200"
        String maturityDate,  // "17.08.2031"
        String issueType      // Tahvil / Kira Sertifikası / Değişim / Ek İhraç
) {}
