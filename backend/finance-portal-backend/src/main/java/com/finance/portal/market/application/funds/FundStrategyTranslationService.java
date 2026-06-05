package com.finance.portal.market.application.funds;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.news.application.port.TranslationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Fon "Yatırım Stratejisi" metni TEFAS/Rasyonet'ten yalnız Türkçe gelir. İngilizce dil
 * modunda kullanıcıya İngilizce göstermek için Google (ücretsiz) çeviriyle çevirir.
 *
 * <p>Strateji metni fon başına SABİT olduğundan sonuç {@code market.fund.strategy.en}
 * cache'ine fon koduna göre konur (her istekte Google'a gitmez). Çeviri başarısızsa
 * orijinal Türkçe metin döner (best-effort, sayfa bozulmaz).</p>
 */
@Service
public class FundStrategyTranslationService {

    private static final Logger log = LoggerFactory.getLogger(FundStrategyTranslationService.class);

    private final TranslationPort translationPort;

    public FundStrategyTranslationService(TranslationPort translationPort) {
        this.translationPort = translationPort;
    }

    /**
     * Fon detayını, stratejisi hedef dile çevrilmiş bir KOPYA olarak döndürür.
     *
     * <p>KRİTİK: {@code getFundDetailRich} {@code @Cacheable} olduğundan dönen DTO cache'teki
     * paylaşılan objedir — doğrudan {@code setStrategy} ile mutate etmek cache'i kirletir
     * (sonraki TR isteği de çevrilmiş görür). Bu yüzden sığ bir kopya üretip yalnız onun
     * stratejisini değiştiririz. tgt boş/"tr" ise ÇEVİRİ YAPILMAZ, orijinal DTO döner.</p>
     */
    public RasyonetFundDetailDto withTranslatedStrategy(RasyonetFundDetailDto detail, String targetLang) {
        if (detail == null || detail.getStrategy() == null || detail.getStrategy().isBlank()
                || targetLang == null || targetLang.isBlank() || "tr".equalsIgnoreCase(targetLang)) {
            return detail;
        }
        String translated = translate(detail.getCode(), detail.getStrategy(), targetLang);
        if (translated == null || translated.equals(detail.getStrategy())) {
            return detail; // çeviri başarısız/aynı → kopya üretme, orijinali döndür
        }
        RasyonetFundDetailDto copy = shallowCopyWithStrategy(detail, translated);
        return copy != null ? copy : detail;
    }

    /** Çeviri (cache'li, fon+dil başına). Strateji fon başına sabit → her istekte Google'a gitmez. */
    @Cacheable(cacheNames = "market.fund.strategy.translated",
            key = "#fundCode + ':' + #targetLang",
            unless = "#result == null")
    public String translate(String fundCode, String strategyTr, String targetLang) {
        try {
            String t = translationPort.translate(strategyTr, "tr", targetLang);
            return (t != null && !t.isBlank()) ? t : strategyTr;
        } catch (Exception e) {
            log.warn("[FundStrategy] çeviri başarısız (fund={}, tgt={}): {}", fundCode, targetLang, e.getMessage());
            return strategyTr; // best-effort
        }
    }

    /**
     * DTO'nun stratejisi değiştirilmiş sığ kopyası (cache'lenmiş orijinali bozmamak için).
     * Tüm alanlar kopyalanır, yalnız strategy override edilir. Hata olursa null döner.
     */
    private static RasyonetFundDetailDto shallowCopyWithStrategy(RasyonetFundDetailDto src, String newStrategy) {
        try {
            RasyonetFundDetailDto c = new RasyonetFundDetailDto();
            org.springframework.beans.BeanUtils.copyProperties(src, c);
            c.setStrategy(newStrategy);
            return c;
        } catch (Exception e) {
            log.warn("[FundStrategy] DTO kopyalanamadı (fund={}): {}", src.getCode(), e.getMessage());
            return null;
        }
    }
}
