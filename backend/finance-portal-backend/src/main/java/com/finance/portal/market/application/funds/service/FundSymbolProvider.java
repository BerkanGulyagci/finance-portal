package com.finance.portal.market.application.funds.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class FundSymbolProvider {

    private static final List<String> FUND_SYMBOLS = List.of(
            "SPY", "QQQ", "IWM", "GLD", "TLT",
            "VTI", "VOO", "EFA", "EEM", "AGG",
            "XLF", "XLK", "XLE", "XLV", "USO"
    );

    public int getTotalElements() {
        return FUND_SYMBOLS.size();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 50) throw new IllegalArgumentException("size must be between 1 and 50");

        int total = FUND_SYMBOLS.size();
        int start = page * size;
        if (start >= total) return Collections.emptyList();
        return FUND_SYMBOLS.subList(start, Math.min(start + size, total));
    }
}
