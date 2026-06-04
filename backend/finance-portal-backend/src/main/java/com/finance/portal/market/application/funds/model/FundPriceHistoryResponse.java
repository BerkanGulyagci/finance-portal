package com.finance.portal.market.application.funds.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Fon fiyat geçmişi yanıtı (grafik için). Kaynak: TEFAS resmi API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundPriceHistoryResponse {

    private String code;
    private String range;
    private String source;
    private List<FundPriceHistoryPoint> points;
}
