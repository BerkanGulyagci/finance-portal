package com.finance.portal.market.application.futures;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class FuturesSymbolProvider {

    private static final List<String> FUTURES_SYMBOLS = List.of(
            // Endeks vadeli
            "ES=F", "NQ=F", "YM=F", "RTY=F",
            // Emtia
            "GC=F", "SI=F", "CL=F", "BZ=F", "NG=F", "HG=F",
            // Tarım
            "ZW=F", "ZC=F", "ZS=F",
            // Döviz vadeli
            "6E=F", "6J=F", "6B=F"
    );

    public int getTotalElements() {
        return FUTURES_SYMBOLS.size();
    }

    public List<String> getAllSymbols() {
        return FUTURES_SYMBOLS;
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        int total = FUTURES_SYMBOLS.size();
        int startIndex = page * size;

        if (startIndex >= total) {
            return Collections.emptyList();
        }

        int endIndex = Math.min(startIndex + size, total);
        return FUTURES_SYMBOLS.subList(startIndex, endIndex);
    }
}

