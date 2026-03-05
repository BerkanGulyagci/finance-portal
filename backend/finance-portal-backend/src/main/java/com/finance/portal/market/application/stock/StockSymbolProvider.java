package com.finance.portal.market.application.stock;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class StockSymbolProvider {

    private static final List<String> BIST30_SYMBOLS = List.of(
            "THYAO.IS", "AKBNK.IS", "ASELS.IS", "EREGL.IS", "FROTO.IS",
            "ISCTR.IS", "KCHOL.IS", "KOZAL.IS", "PETKM.IS", "SAHOL.IS"
    );

    public int getTotalElements() {
        return BIST30_SYMBOLS.size();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 20) {
            throw new IllegalArgumentException("size must be between 1 and 20");
        }

        int total = BIST30_SYMBOLS.size();
        int startIndex = page * size;

        if (startIndex >= total) {
            return Collections.emptyList();
        }

        int endIndex = Math.min(startIndex + size, total);
        return BIST30_SYMBOLS.subList(startIndex, endIndex);
    }
}

