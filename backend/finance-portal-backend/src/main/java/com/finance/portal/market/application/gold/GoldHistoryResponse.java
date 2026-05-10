package com.finance.portal.market.application.gold;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class GoldHistoryResponse implements Serializable {

    private String symbol;
    private String range;
    private String currency;
    private List<GoldHistoryPoint> points;

    /** "Borsa İstanbul" veya "Yahoo Finance Fallback" */
    private String source;

    /** Resmi BIST verisi mi? */
    private boolean official;

    /** BIST erişilemedi, fallback kullanıldı mı? */
    private boolean fallback;

    /** Veri güncel değil mi (hafta sonu / tatil)? */
    private boolean stale;

    private String lastUpdated;

    /**
     * Kullanıcıya gösterilecek uyarı metni.
     */
    private String disclaimer;
}
