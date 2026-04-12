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
            "A1CAP.IS", "ACSEL.IS", "ADEL.IS", "ADESE.IS", "AKMGY.IS",
            "AKSA.IS", "AKSEN.IS", "ALFAS.IS", "ALKIM.IS", "ALTNY.IS",
            "ANELE.IS", "ARASE.IS", "ARSAN.IS", "ATAGY.IS", "ATAKP.IS",
            "AVGYO.IS", "AYCES.IS", "AYEN.IS", "BAGFS.IS", "BAKAB.IS",
            "BANVT.IS", "BASGZ.IS", "BERA.IS", "BEYAZ.IS", "BFREN.IS",
            "BIENY.IS", "BIGCH.IS", "BINHO.IS", "BIOEN.IS", "BIZIM.IS",
            "BJKAS.IS", "BMELK.IS", "BNTAS.IS", "BOSSA.IS", "BUCIM.IS",
            "BURCE.IS", "BURVA.IS", "BVSAN.IS", "CANTE.IS", "CELHA.IS",
            "CEMAS.IS", "CEMTS.IS", "CEOEM.IS", "CLEBI.IS", "CMBTN.IS",
            "CMENT.IS", "CONSE.IS", "COSMO.IS", "CRDFA.IS", "CRFSA.IS",
            "CUSAN.IS", "CVKMD.IS", "CWENE.IS", "DAGHL.IS", "DAGI.IS",
            "DAPGM.IS", "DARDL.IS", "DENGE.IS", "DERHL.IS", "DERIM.IS",
            "DESA.IS", "DESPC.IS", "DEVA.IS", "DGATE.IS", "DGGYO.IS",
            "DITAS.IS", "DMRGD.IS", "DMSAS.IS", "DNISI.IS", "DOBUR.IS",
            "DOCO.IS", "DOGUB.IS", "DOHOL.IS", "DOKTA.IS", "DURDO.IS",
            "DYOBY.IS", "DZGYO.IS", "ECILC.IS", "ECZYT.IS", "EDATA.IS",
            "EDIP.IS", "EGEEN.IS", "EGEPO.IS", "EGGUB.IS", "EGPRO.IS",
            "EGSER.IS", "EMKEL.IS", "EMNIS.IS", "ENERY.IS", "ENGYO.IS",
            "EPLAS.IS", "ERBOS.IS", "ERCB.IS", "ERSU.IS", "ESCAR.IS",
            "ESCOM.IS", "ESEN.IS", "ETILR.IS", "ETYAT.IS", "EUHOL.IS",
            "EUKYO.IS", "EUPWR.IS", "EUREN.IS", "EUYO.IS", "EYGYO.IS",
            "FADE.IS", "FENER.IS", "FLAP.IS", "FMIZP.IS", "FONET.IS",
            "FORMT.IS", "FORTE.IS", "FRIGO.IS", "FZLGY.IS", "GARAN.IS",
            "GARFA.IS", "GEDIK.IS", "GEDZA.IS", "GENTS.IS", "GEREL.IS",
            "GLBMD.IS", "GLCVY.IS", "GLRYH.IS", "GLYHO.IS", "GMTAS.IS",
            "GOKNR.IS", "GOLTS.IS", "GOODY.IS", "GOZDE.IS", "GRSEL.IS",
            "GRTRK.IS", "GSDDE.IS", "GSDHO.IS", "GSRAY.IS", "GWIND.IS",
            "GZNMI.IS", "HDFGS.IS", "HEDEF.IS", "HKTM.IS", "HLGYO.IS",
            "HRKET.IS", "HTTBT.IS", "HUBVC.IS", "HUNER.IS", "HURGZ.IS",
            "ICBCT.IS", "ICUGS.IS", "IDEAS.IS", "IDGYO.IS", "IEYHO.IS",
            "IHEVA.IS", "IHGZT.IS", "IHLAS.IS", "IHLGM.IS", "IHYAY.IS",
            "IMASM.IS", "INDES.IS", "INFO.IS", "INGRM.IS", "INTEM.IS",
            "INVEO.IS", "IPEKE.IS", "ISATR.IS", "ISBIR.IS", "ISDMR.IS",
            "ISFIN.IS", "ISGSY.IS", "ISKPL.IS", "ISYAT.IS", "ITTFH.IS",
            "IZFAS.IS", "IZINV.IS", "IZMDC.IS", "JANTS.IS", "KAPLM.IS",
            "KAREL.IS", "KATMR.IS", "KAYSE.IS", "KBORU.IS", "KCAER.IS",
            "KCHOL.IS", "KENT.IS", "KERVN.IS", "KERVT.IS", "KFEIN.IS",
            "KGYO.IS", "KIMMR.IS", "KLGYO.IS", "KLKIM.IS", "KLMSN.IS",
            "KLNMA.IS", "KLRHO.IS", "KLSER.IS", "KMPUR.IS", "KNFRT.IS",
            "KONKA.IS", "KONTR.IS", "KONYA.IS", "KOPOL.IS", "KORDS.IS",
            "KOTON.IS", "KRDMA.IS", "KRDMB.IS", "KRONT.IS", "KRPLS.IS",
            "KRSTL.IS", "KRTEK.IS", "KRVGD.IS", "KSTUR.IS", "KTLEV.IS",
            "KUTPO.IS", "KUVVA.IS", "KUYAS.IS", "KZBGY.IS", "KZGYO.IS",
            "LIDER.IS", "LIDFA.IS", "LILAK.IS", "LKMNH.IS", "LRSHO.IS",
            "LUKSK.IS", "MAALT.IS", "MACKO.IS", "MAGEN.IS", "MAKIM.IS",
            "MAKTK.IS", "MANAS.IS", "MARKA.IS", "MARTI.IS", "MATUR.IS",
            "MEDTR.IS", "MEGAP.IS", "MEKAG.IS", "MEKON.IS", "MEPET.IS",
            "MERCN.IS", "MERIT.IS", "MERKO.IS", "METRO.IS", "METUR.IS",
            "MIATK.IS", "MIGROS.IS", "MIPAZ.IS", "MNDRS.IS", "MNDTR.IS",
            "MOBTL.IS", "MOGAN.IS", "MPARK.IS", "MRGYO.IS", "MRSHL.IS",
            "MSGYO.IS", "MTRKS.IS", "MZHLD.IS", "NATEN.IS", "NETAS.IS",
            "NIBAS.IS", "NILYT.IS", "NTHOL.IS", "NTTUR.IS", "NUGYO.IS",
            "NUHCM.IS", "OBAMS.IS", "OBASE.IS", "ODAS.IS", "OFSYM.IS",
            "ONCSM.IS", "ORCAY.IS", "ORGE.IS", "ORMA.IS", "OSMEN.IS",
            "OSTIM.IS", "OYAYO.IS", "OYLUM.IS", "OYYAT.IS", "OZGYO.IS",
            "OZKGY.IS", "OZRDN.IS", "OZSUB.IS", "PAGYO.IS", "PAMEL.IS",
            "PAPIL.IS", "PCILT.IS", "PEHOL.IS", "PENGD.IS", "PENTA.IS",
            "PETKM.IS", "PETUN.IS", "PGSUS.IS", "PINSU.IS", "PKART.IS",
            "PKENT.IS", "PLTUR.IS", "PNLSN.IS", "POLHO.IS", "POLTK.IS",
            "PRDGS.IS", "PRKAB.IS", "PRKME.IS", "PRZMA.IS", "PSDTC.IS",
            "PSGYO.IS", "QNBFB.IS", "QNBFL.IS", "QUAGR.IS", "RALYH.IS",
            "RAYSG.IS", "REEDR.IS", "RGYAS.IS", "RNPOL.IS", "RODRG.IS",
            "ROYAL.IS", "RTALB.IS", "RUBNS.IS", "RYGYO.IS", "RYSAS.IS",
            "SAFKR.IS", "SAHOL.IS", "SAMAT.IS", "SANEL.IS", "SANFM.IS",
            "SANKO.IS", "SARKY.IS", "SASA.IS", "SAYAS.IS", "SDTTR.IS",
            "SEGYO.IS", "SEKFK.IS", "SEKUR.IS", "SELEC.IS", "SELGD.IS",
            "SELVA.IS", "SEYKM.IS", "SILVR.IS", "SISE.IS", "SKBNK.IS",
            "SKYLP.IS", "SMART.IS", "SMRTG.IS", "SNGYO.IS", "SNKRN.IS",
            "SNPAM.IS", "SODSN.IS", "SOKE.IS", "SOKM.IS", "SONME.IS",
            "SRVGY.IS", "SUMAS.IS", "SUNTK.IS", "SUWEN.IS", "TABGD.IS",
            "TARKM.IS", "TATEN.IS", "TATGD.IS", "TAVHL.IS", "TBORG.IS",
            "TCELL.IS", "TDGYO.IS", "TEKTU.IS", "TERA.IS", "TETMT.IS",
            "TEZOL.IS", "TGSAS.IS", "THYAO.IS", "TKFEN.IS", "TKNSA.IS",
            "TLMAN.IS", "TMPOL.IS", "TMSN.IS", "TNZTP.IS", "TOASO.IS",
            "TRCAS.IS", "TRGYO.IS", "TRILC.IS", "TSGYO.IS", "TSKB.IS",
            "TSPOR.IS", "TTKOM.IS", "TTRAK.IS", "TUCLK.IS", "TUKAS.IS",
            "TUPRS.IS", "TUREX.IS", "TURGG.IS", "TURSG.IS", "ULUFA.IS",
            "ULUSE.IS", "ULUUN.IS", "UMPAS.IS", "UNLU.IS", "USAK.IS",
            "USDTR.IS", "UTPYA.IS", "UZERB.IS", "VAKBN.IS", "VAKFN.IS",
            "VAKKO.IS", "VANGD.IS", "VBTYZ.IS", "VERTU.IS", "VERUS.IS",
            "VESBE.IS", "VESTL.IS", "VKFYO.IS", "VKGYO.IS", "VRGYO.IS",
            "WINTE.IS", "XTLCO.IS", "YAPRK.IS", "YATAS.IS", "YBTAS.IS",
            "YEOTK.IS", "YESIL.IS", "YGGYO.IS", "YGYO.IS", "YKSLN.IS",
            "YONGA.IS", "YUNSA.IS", "YYAPI.IS", "ZEDUR.IS", "ZRGYO.IS",
            "ZRTEK.IS"
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
