package com.finance.portal.market.application.crypto.model;

import java.util.List;

/**
 * Crypto Fear &amp; Greed Index için CoinMarketCap tarzı ZENGİN özet — gauge sol paneli.
 *
 * <p>{@link FearGreedService#getSummary(int)} alternative.me'den tek çağrıda 365 gün çekip
 * hesaplar: anlık değer + geçmiş kıyaslar (dün/geçen hafta/geçen ay) + yıllık min/max +
 * grafik için son {@code days} günlük seri.</p>
 *
 * <p>Tüm {@code timestamp} alanları MİLİSANİYE cinsindendir (frontend ms bekler;
 * {@link FearGreedPoint} ile tutarlı). Veri yetersizse ilgili snapshot {@code null} olabilir
 * (ör. 7 günden az veri varsa {@code lastWeek}).</p>
 *
 * @param current     en güncel ölçüm (alternative.me indeks 0) — değer/sınıf/zaman
 * @param yesterday   dünkü ölçüm (indeks 1); veri yoksa {@code null}
 * @param lastWeek    7 gün önceki ölçüm (indeks 7); veri yoksa {@code null}
 * @param lastMonth   30 gün önceki ölçüm (indeks 30); veri yoksa {@code null}
 * @param yearlyHigh  son 365 gündeki en yüksek değer (ilk görülen güne göre, zaman dahil)
 * @param yearlyLow   son 365 gündeki en düşük değer (ilk görülen güne göre, zaman dahil)
 * @param series      grafik için son {@code days} günlük seri (en yeni önce, kaynak sırasıyla)
 */
public record FearGreedSummary(
        FearGreedSnapshot current,
        FearGreedSnapshot yesterday,
        FearGreedSnapshot lastWeek,
        FearGreedSnapshot lastMonth,
        FearGreedSnapshot yearlyHigh,
        FearGreedSnapshot yearlyLow,
        List<FearGreedPoint> series
) {

    /**
     * Tek bir Fear &amp; Greed anlık değeri (gauge/kıyas kutuları için).
     *
     * @param value          endeks değeri 0-100 (0=Aşırı Korku, 100=Aşırı Açgözlülük)
     * @param classification metin sınıf (Extreme Fear / Fear / Neutral / Greed / Extreme Greed)
     * @param timestamp      ölçüm zamanı epoch MİLİSANİYE; bilinmiyorsa {@code null}
     */
    public record FearGreedSnapshot(int value, String classification, Long timestamp) {

        /** {@link FearGreedPoint}'tan snapshot üretir (timestamp dahil). */
        public static FearGreedSnapshot of(FearGreedPoint p) {
            return new FearGreedSnapshot(p.value(), p.classification(), p.timestamp());
        }

        /** Geçmiş kıyas kutuları için timestamp'siz snapshot (değer + sınıf yeterli). */
        public static FearGreedSnapshot ofValueOnly(FearGreedPoint p) {
            return new FearGreedSnapshot(p.value(), p.classification(), null);
        }
    }
}
