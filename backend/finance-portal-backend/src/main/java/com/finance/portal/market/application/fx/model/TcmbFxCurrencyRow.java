package com.finance.portal.market.application.fx.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TcmbFxCurrencyRow {

    private String currencyCode;
    private Integer unit;
    private String forexBuying;
    private String forexSelling;
    private String banknoteBuying;
    private String banknoteSelling;

    public TcmbFxCurrencyRow(String currencyCode, Integer unit, String forexBuying, String forexSelling) {
        this(currencyCode, unit, forexBuying, forexSelling, null, null);
    }
}
