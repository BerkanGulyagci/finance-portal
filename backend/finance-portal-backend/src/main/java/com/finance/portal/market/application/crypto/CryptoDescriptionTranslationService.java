package com.finance.portal.market.application.crypto;

import com.finance.portal.news.application.port.TranslationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Kripto detayındaki "X Hakkında" açıklaması CoinGecko'dan çok dilli bir map ({@code description.{tr,en,...}})
 * olarak gelir. Ancak yeni/küçük coinler (örn. RAIN) için CoinGecko TÜRKÇE açıklama VERMEZ →
 * {@code description.tr} boş kalır ve UI İngilizce metne düşer.
 *
 * <p>Bu servis, TR dili istendiğinde ve TR açıklama boşken İngilizce açıklamayı Google (ücretsiz)
 * çeviriyle Türkçeye çevirir. Açıklama metni coin başına SABİT olduğundan sonuç
 * {@code market.crypto.description.translated} cache'ine coin koduna göre konur (her istekte
 * Google'a gidilmez — çeviri uzun ve maliyetlidir). Çeviri başarısızsa orijinal İngilizce metin
 * döner (best-effort, sayfa bozulmaz). HTML etiketleri (CoinGecko {@code <a>} linkleri) çeviride
 * korunur — Google translate HTML markup'ını olduğu gibi geçirir.</p>
 */
@Service
public class CryptoDescriptionTranslationService {

    private static final Logger log = LoggerFactory.getLogger(CryptoDescriptionTranslationService.class);

    private final TranslationPort translationPort;

    public CryptoDescriptionTranslationService(TranslationPort translationPort) {
        this.translationPort = translationPort;
    }

    /**
     * İngilizce açıklamayı Türkçeye çevirir (cache'li, coin başına). Yalnız TR açıklama boş + EN
     * doluyken çağrılmalıdır. Çeviri başarısız/aynı dönerse orijinal EN metni döner.
     *
     * @param coinId      cache anahtarı (coin başına tek çeviri)
     * @param descriptionEn çevrilecek İngilizce açıklama (HTML içerebilir)
     * @return Türkçe açıklama; hata durumunda orijinal İngilizce metin
     */
    @Cacheable(cacheNames = "market.crypto.description.translated",
            key = "#coinId",
            unless = "#result == null")
    public String translateToTurkish(String coinId, String descriptionEn) {
        if (descriptionEn == null || descriptionEn.isBlank()) {
            return descriptionEn;
        }
        try {
            String t = translationPort.translate(descriptionEn, "en", "tr");
            return (t != null && !t.isBlank()) ? t : descriptionEn;
        } catch (Exception e) {
            log.warn("[CryptoDescription] çeviri başarısız (coin={}): {}", coinId, e.getMessage());
            return descriptionEn; // best-effort
        }
    }
}
