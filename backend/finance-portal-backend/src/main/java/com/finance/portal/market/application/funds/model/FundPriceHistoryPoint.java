package com.finance.portal.market.application.funds.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tek bir günün fon fiyat noktası (TEFAS tarihsel seri).
 * Redis cache + REST yanıtı için POJO (no-arg ctor + getter/setter).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundPriceHistoryPoint {

    private String date;            // yyyy-MM-dd
    private BigDecimal price;       // birim pay fiyatı (NAV)
    private BigDecimal portfolioSize; // portföy büyüklüğü (TL), opsiyonel
}
