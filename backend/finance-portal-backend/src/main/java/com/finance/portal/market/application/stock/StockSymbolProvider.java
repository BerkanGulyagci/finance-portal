package com.finance.portal.market.application.stock;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class StockSymbolProvider {

    private static final List<String> BIST30_SYMBOLS = List.of(
            // BIST 30
            "THYAO.IS", "AKBNK.IS", "ASELS.IS", "EREGL.IS", "FROTO.IS",
            "ISCTR.IS", "KCHOL.IS", "KOZAL.IS", "PETKM.IS", "SAHOL.IS",
            "GARAN.IS", "HALKB.IS", "VAKBN.IS", "YKBNK.IS", "SISE.IS",
            "TOASO.IS", "TUPRS.IS", "BIMAS.IS", "MGROS.IS", "ARCLK.IS",
            "TCELL.IS", "TTKOM.IS", "ENKAI.IS", "EKGYO.IS", "PGSUS.IS",
            "TAVHL.IS", "VESTL.IS", "KRDMD.IS", "SOKM.IS", "KOZAA.IS",
            // BIST 50 ek
            "AEFES.IS", "AGHOL.IS", "ALARK.IS", "ANACM.IS", "BRISA.IS",
            "CCOLA.IS", "CIMSA.IS", "DOHOL.IS", "EGEEN.IS", "ENJSA.IS",
            "GESAN.IS", "GUBRF.IS", "HEKTS.IS", "IPEKE.IS", "ISGYO.IS",
            "KARSN.IS", "LOGO.IS",  "MAVI.IS",  "NETAS.IS", "ODAS.IS",
            "OTKAR.IS", "OYAKC.IS", "PARSN.IS", "PRKME.IS", "QUAGR.IS",
            "SASA.IS",  "SKBNK.IS", "TSKB.IS",  "ULKER.IS", "ZOREN.IS"
    );

    public int getTotalElements() {
        return BIST30_SYMBOLS.size();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
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

