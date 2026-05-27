package com.finance.portal.market.application.bond.eurobond.port;

import com.finance.portal.market.application.bond.eurobond.model.HmbBond;

import java.util.List;

/** HMB "Merkezi Yönetim Dış Borç Stoku Tahvil Listesi" ISIN kümesi (aylık güncellenir). */
public interface HmbIsinSource {

    /** Güncel ISIN listesi (son başarılı xlsx parse'ı ya da yedek tohum listesi). */
    List<String> isins();

    /** Aktif ISIN'ler + statik künye (tohum CSV'sinden; bilinmeyen alanlar null). Sıralama isins() ile aynı. */
    List<HmbBond> bonds();

    /** Verilen xlsx URL'inden ISIN'leri indirip parse eder ve aktif listeyi günceller; toplam aktif ISIN sayısını döndürür. */
    default int refreshFromXlsx(String xlsxUrl) {
        return refreshFromXlsx(xlsxUrl, false);
    }

    /**
     * {@link #refreshFromXlsx(String)} ile aynı; ancak {@code force=true} verilirse URL filename
     * keyword check'i (varsayılan: filename'de "dis_borc_stoku" geçmesi gerekir) atlanır.
     * Bu, HMB'nin filename şeması ileride değişirse admin için elle override imkânı sağlar.
     * İçerik doğrulamaları (min ISIN sayısı, TR prefix oranı, mevcutla overlap) {@code force} olsa bile uygulanır.
     */
    int refreshFromXlsx(String xlsxUrl, boolean force);

    /** En son başarıyla kullanılan xlsx URL'i (yoksa null) — zamanlanmış yeniden çekim için. */
    String lastXlsxUrl();
}
