package com.finance.portal.market.application.bond.eurobond.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bir eurobond'un belirli bir tarihteki (genelde işlem tarihi) <b>kirli fiyat dökümü</b> — işlem
 * ekleme modalında otomatik doldurma için. Tüm TL değerleri <b>o tarihteki TCMB kuruyla</b>
 * hesaplanır (tarihsel FX gömülü; Model 1 ile tutarlı). Kupon konvansiyonu kaynakta yer almadığı
 * için birikmiş faiz <b>tahmini</b>dir.
 *
 * @param found             o tarih için fiyat bulunabildi mi
 * @param priceDate         fiyatın ait olduğu gerçek işlem günü (istenen tarih hafta sonu/tatilse en yakın önceki)
 * @param currency          tahvilin döviz cinsi (USD/EUR/JPY)
 * @param cleanPriceTry     temiz (kote) fiyatın o günkü TL karşılığı
 * @param accruedInterestTry birikmiş faizin o günkü TL karşılığı (tahmini)
 * @param dirtyPriceTry     kirli fiyatın TL karşılığı = cleanPriceTry + accruedInterestTry (modalda kullanılacak)
 * @param cleanPriceQuote   temiz kote fiyat (döviz cinsi, 100 nominal başına) — bilgi/şeffaflık için
 * @param accruedInterest   birikmiş faiz (döviz cinsi, 100 nominal başına) — bilgi için
 * @param dayCountConvention kullanılan gün-sayım konvansiyonu adı (THIRTY_360 / ACT_ACT / ACT_365)
 */
public record EurobondPriceAt(
        boolean found,
        LocalDate priceDate,
        String currency,
        BigDecimal cleanPriceTry,
        BigDecimal accruedInterestTry,
        BigDecimal dirtyPriceTry,
        BigDecimal cleanPriceQuote,
        BigDecimal accruedInterest,
        String dayCountConvention) {

    public static EurobondPriceAt notFound() {
        return new EurobondPriceAt(false, null, null, null, null, null, null, null, null);
    }
}
