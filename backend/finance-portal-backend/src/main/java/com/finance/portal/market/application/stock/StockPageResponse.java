package com.finance.portal.market.application.stock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class StockPageResponse {

    private List<StockSummary> content;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
}
