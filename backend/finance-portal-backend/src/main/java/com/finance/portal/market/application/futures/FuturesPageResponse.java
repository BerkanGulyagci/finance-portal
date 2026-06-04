package com.finance.portal.market.application.futures;

import com.finance.portal.market.application.stock.StockSummary;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FuturesPageResponse {

    private List<StockSummary> content;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
}

