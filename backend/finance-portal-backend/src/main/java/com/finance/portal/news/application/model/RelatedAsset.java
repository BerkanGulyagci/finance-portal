package com.finance.portal.news.application.model;

/**
 * Bir haberde tespit edilen ilgili varlık (haber→grafik geçişi için).
 *
 * @param symbol enstrüman sembolü/kodu (ör. THYAO, bitcoin, USD) — frontend route'u bununla kurar
 * @param name   görünen ad (ör. "Türk Hava Yolları", "Bitcoin", "Dolar")
 * @param type   varlık türü: STOCK | CRYPTO | FX (frontend doğru detay sayfasına yönlendirir)
 */
public record RelatedAsset(String symbol, String name, String type) {
}
