package com.finance.portal.market.application.crypto.model;

/**
 * Crypto Fear &amp; Greed Index'in tek günlük noktası.
 *
 * <p>Kaynak: alternative.me (/fng). Frontend grafiği coin fiyatıyla karşılaştırmalı
 * çizdiği için {@code timestamp} MİLİSANİYE cinsindendir (alternative.me unix SANİYE
 * döner → client'a verirken ×1000).</p>
 *
 * @param timestamp      ölçüm zamanı, epoch MİLİSANİYE (frontend ms bekler)
 * @param value          endeks değeri 0-100 (0=Aşırı Korku, 100=Aşırı Açgözlülük)
 * @param classification metin sınıf (Extreme Fear / Fear / Neutral / Greed / Extreme Greed)
 */
public record FearGreedPoint(long timestamp, int value, String classification) {
}
