package com.finance.portal.market.application.fx.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxRateItem {

    private String symbol;
    private BigDecimal buy;
    private BigDecimal sell;
    private int unit;
    /** Efektif (banknot) alış — yalnızca TCMB kaynağında doludur, aksi halde null. */
    private BigDecimal effectiveBuy;
    /** Efektif (banknot) satış — yalnızca TCMB kaynağında doludur, aksi halde null. */
    private BigDecimal effectiveSell;

    public FxRateItem(String symbol, BigDecimal buy, BigDecimal sell, int unit) {
        this.symbol = symbol;
        this.buy = buy;
        this.sell = sell;
        this.unit = unit;
    }
}
