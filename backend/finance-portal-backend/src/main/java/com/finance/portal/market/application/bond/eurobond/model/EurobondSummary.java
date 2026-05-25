package com.finance.portal.market.application.bond.eurobond.model;

import java.math.BigDecimal;

/** Eurobond liste satırı (HMB künyesi + BI canlı fiyatı). hasDetail=false ise BI çözülemedi (yedek satır). */
public record EurobondSummary(
        String isin,
        String name,
        String issuer,
        String currency,
        String couponRate,
        String maturityDate,
        String issueType,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        Long instrumentId,
        boolean hasDetail
) {}
