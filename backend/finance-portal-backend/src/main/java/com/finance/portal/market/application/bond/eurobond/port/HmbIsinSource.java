package com.finance.portal.market.application.bond.eurobond.port;

import com.finance.portal.market.application.bond.eurobond.model.HmbBond;

import java.util.List;

/** HMB "Merkezi Yönetim Dış Borç Stoku Tahvil Listesi" ISIN kümesi (aylık güncellenir). */
public interface HmbIsinSource {

    /** Güncel ISIN listesi (son başarılı xlsx parse'ı ya da yedek tohum listesi). */
    List<String> isins();

    /** Aktif ISIN'ler + statik künye (tohum CSV'sinden; bilinmeyen alanlar null). Sıralama isins() ile aynı. */
    List<HmbBond> bonds();

    /** Verilen xlsx URL'inden ISIN'leri indirip parse eder ve aktif listeyi günceller; eklenen/çıkan sayısını döndürür. */
    int refreshFromXlsx(String xlsxUrl);

    /** En son başarıyla kullanılan xlsx URL'i (yoksa null) — zamanlanmış yeniden çekim için. */
    String lastXlsxUrl();
}
