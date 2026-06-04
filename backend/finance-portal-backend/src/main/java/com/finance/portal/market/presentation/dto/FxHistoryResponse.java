package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxHistoryResponse {
    private String symbol;
    private String quoteCurrency;
    private String range;
    private List<FxHistoryPoint> points;
}
