package com.finance.portal.market.infrastructure.external.gold;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BIST Kıymetli Madenler endpoint'inden dönen tarihsel altın verisi.
 * GoldMarketService tarafından üretilir ve GoldHistoryResponse'a dönüştürülür.
 */
@Data
@NoArgsConstructor
public class BistGoldHistoryResponse {

    private String source = "Borsa İstanbul";
    private boolean official = true;
    private String unit = "TL/Kg";
    private String gramUnit = "TL/Gr";
    private String startDate;
    private String endDate;

    /** Veri noktaları — eski → yeni (ASC) sıralı */
    private List<BistGoldHistoricalPoint> points;

    /** BIST erişilemedi, Yahoo fallback kullanıldı mı? */
    private boolean fallback;

    /** Veri güncel değil mi (hafta sonu / tatil)? */
    private boolean stale;

    private String lastUpdated;
}
