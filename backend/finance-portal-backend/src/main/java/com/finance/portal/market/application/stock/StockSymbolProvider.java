package com.finance.portal.market.application.stock;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class StockSymbolProvider {

    private static final List<String> BIST_SYMBOLS = List.of(
            // BIST 30
            "THYAO.IS", "AKBNK.IS", "ASELS.IS", "EREGL.IS", "FROTO.IS",
            "ISCTR.IS", "KCHOL.IS", "KOZAL.IS", "PETKM.IS", "SAHOL.IS",
            "GARAN.IS", "HALKB.IS", "VAKBN.IS", "YKBNK.IS", "SISE.IS",
            "TOASO.IS", "TUPRS.IS", "BIMAS.IS", "MGROS.IS", "ARCLK.IS",
            "TCELL.IS", "TTKOM.IS", "ENKAI.IS", "EKGYO.IS", "PGSUS.IS",
            "TAVHL.IS", "VESTL.IS", "KRDMD.IS", "SOKM.IS", "KOZAA.IS",
            // BIST 50 ek
            "AEFES.IS", "AGHOL.IS", "ALARK.IS", "ANACM.IS", "BRISA.IS",
            "CCOLA.IS", "CIMSA.IS", "DOHOL.IS", "ENJSA.IS", "GESAN.IS",
            "GUBRF.IS", "HEKTS.IS", "ISGYO.IS", "KARSN.IS", "LOGO.IS",
            "MAVI.IS", "OTKAR.IS", "OYAKC.IS", "SASA.IS", "SKBNK.IS",
            "TSKB.IS", "ULKER.IS", "ZOREN.IS", "ODAS.IS", "PARSN.IS",
            // BIST 100 ek
            "A1CAP.IS", "AKSA.IS", "AKSEN.IS", "ALKIM.IS", "BANVT.IS",
            "BERA.IS", "BIZIM.IS", "BJKAS.IS", "BUCIM.IS", "BURCE.IS",
            "CEMAS.IS", "CLEBI.IS", "DESA.IS", "DEVA.IS", "DITAS.IS",
            "ECILC.IS", "ECZYT.IS", "EGEEN.IS", "EMKEL.IS", "ERBOS.IS",
            "ESCAR.IS", "FENER.IS", "GSRAY.IS", "HDFGS.IS", "HUBVC.IS",
            "IHEVA.IS", "IHLAS.IS", "IMASM.IS", "INDES.IS", "INFO.IS",
            "IPEKE.IS", "ISATR.IS", "ISBIR.IS", "ISDMR.IS", "ISFIN.IS",
            "ISGSY.IS", "JANTS.IS", "KAREL.IS", "KATMR.IS", "KBORU.IS",
            "KENT.IS", "KERVT.IS", "KLKIM.IS", "KLMSN.IS", "KONKA.IS",
            "KONYA.IS", "KORDS.IS", "KOTON.IS", "KRDMA.IS", "KRDMB.IS",
            "KRTEK.IS", "LIDER.IS", "LILAK.IS", "LUKSK.IS", "MAALT.IS",
            "MAGEN.IS", "MAKIM.IS", "MARKA.IS", "MARTI.IS", "MEDTR.IS",
            "MEGAP.IS", "MERCN.IS", "MERIT.IS", "MERKO.IS", "METRO.IS",
            "MNDRS.IS", "MOBTL.IS", "MPARK.IS", "NATEN.IS", "NETAS.IS",
            "NUHCM.IS", "ODAS.IS", "ORGE.IS", "OSTIM.IS", "PENGD.IS",
            "PENTA.IS", "PETUN.IS", "PKART.IS", "PKENT.IS", "PRKAB.IS",
            "PRKME.IS", "QUAGR.IS", "RAYSG.IS", "RGYAS.IS", "ROYAL.IS",
            "SAFKR.IS", "SAMAT.IS", "SANEL.IS", "SANFM.IS", "SARKY.IS",
            "SAYAS.IS", "SELEC.IS", "SELGD.IS", "SELVA.IS", "SILVR.IS",
            "SMART.IS", "SOKE.IS", "SONME.IS", "SUMAS.IS", "SUWEN.IS",
            "TABGD.IS", "TATGD.IS", "TEKTU.IS", "TKFEN.IS", "TKNSA.IS",
            "TLMAN.IS", "TMPOL.IS", "TMSN.IS", "TRCAS.IS", "TRILC.IS",
            "TTRAK.IS", "TUKAS.IS", "TUREX.IS", "ULUFA.IS", "ULUSE.IS",
            "ULUUN.IS", "UMPAS.IS", "UNLU.IS", "USAK.IS", "VAKKO.IS",
            "VESBE.IS", "YAPRK.IS", "YATAS.IS", "YEOTK.IS", "YONGA.IS",
            "YUNSA.IS"
    );

    public int getTotalElements() {
        return BIST_SYMBOLS.size();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        int total = BIST_SYMBOLS.size();
        int start = page * size;
        if (start >= total) return Collections.emptyList();
        return BIST_SYMBOLS.subList(start, Math.min(start + size, total));
    }
}
