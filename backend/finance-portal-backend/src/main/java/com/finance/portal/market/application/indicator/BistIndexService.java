package com.finance.portal.market.application.indicator;

import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * BIST endeks fiyat geçmişi servisi.
 *
 * <p>Backend'in başka hiçbir yerinde BIST endeks fiyatı yayımlanmaz:
 * {@code MidasStockClient} bu sembolleri (XU*) dışlar, {@code AssetType} enum'unda
 * bunlara karşılık gelen bir değer yoktur, {@code ViopService} ise yalnız etiketleme
 * için kullanır. Bu servis MarketTicker (ve genel {@code /api/v1/market/price-history}
 * INDICATOR pseudo-türü) için en minimal eklemeyi yapar: Yahoo Finance'in
 * {@code XU100.IS / XU030.IS / XU050.IS} (Istanbul exchange suffix) sembollerinden
 * günlük kapanış serisi çeker.</p>
 *
 * <p><b>Önemli — sembol formatı:</b> Yahoo, {@code ^XU100} (caret prefix, US-style index)
 * formatını kabul eder ama BOŞ bir quote dizisi döndürür (exchange "Nasdaq GIDS" gösterir,
 * timestamp dizisi yok). Doğru sembol formatı {@code .IS} suffix'idir (Istanbul exchange);
 * curl ile {@code XU100.IS?range=1mo&interval=1d} çağrısı tam timestamp + close[] dizilerini
 * canlı TR market saatiyle döndürür. Caret formatına geri DÖNMEYİN — sessiz veri kaybı olur.</p>
 *
 * <p>Sonuç {@link Cacheable} ile 60 dk önbelleğe alınır — endeksler intraday yavaş
 * hareket ettiği için ticker / detay kullanımına bu yeterli (Yahoo'ya minimum yük).</p>
 */
@Service
public class BistIndexService {

    private static final Logger log = LoggerFactory.getLogger(BistIndexService.class);

    /**
     * BIST sözde-sembol → Yahoo Finance ticker eşlemesi.
     * Istanbul exchange suffix formatı ({@code .IS}) kullanılır — caret prefix ({@code ^XU100})
     * formatı Yahoo tarafından kabul edilse de BOŞ veri döndürür (bkz. sınıf javadoc).
     */
    private static final Map<String, String> YAHOO_SYMBOLS = Map.of(
            "XU100", "XU100.IS",
            "XU030", "XU030.IS",
            "XU050", "XU050.IS"
    );

    private final YahooStockPort yahooStockPort;
    private final CacheManager cacheManager;

    public BistIndexService(YahooStockPort yahooStockPort, CacheManager cacheManager) {
        this.yahooStockPort = yahooStockPort;
        this.cacheManager = cacheManager;
    }

    /**
     * BIST endeks geçmiş cache'ini güvenli şekilde temizler. Redis/cache backend
     * ulaşılamazsa (ör. test ortamında Redis yok) startup'ı ÇÖKERTMEZ — sadece loglar.
     * Eskiden {@code @CacheEvict} annotation'ı kullanılıyordu; o, ApplicationReadyEvent'te
     * Redis'e eager bağlanıp bağlantı yoksa context'i çökertiyordu (CI'da tüm IT'leri kırdı).
     */
    private void clearBistCache(String context) {
        try {
            var cache = cacheManager.getCache("market.bistIndex.history");
            if (cache != null) {
                cache.clear();
            }
            log.info("BIST index history cache evicted ({})", context);
        } catch (RuntimeException e) {
            log.warn("BIST index cache evict atlandı ({}): cache backend ulaşılamıyor — {}", context, e.getMessage());
        }
    }

    /** Verilen sembol (XU100/XU030/XU050) bu servisle desteklenir mi? */
    public boolean supports(String upperSymbol) {
        return upperSymbol != null && YAHOO_SYMBOLS.containsKey(upperSymbol.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Uygulama hazır olduğunda BIST endeks cache'ini bir kez boşalt.
     *
     * <p>Geçmişte {@code ^XU100} (caret prefix) sembol formatı kullanılıyordu — Yahoo bu formatı
     * 200 ile yanıtlasa da {@code timestamps=null} / {@code quote.close=null} boş bir snapshot
     * döndürüyor; Spring @Cacheable ise bu boş yanıtı 60 dk önbelleğe yazıyordu. Sembol
     * formatı {@code XU100.IS} olarak düzeltildiğinde bile, stale Redis girdileri MarketTicker'ın
     * istediği {@code 1mo} range cache key'ini doldurmaya devam ediyordu ve BIST kartları
     * ticker'dan sessizce düşüyordu.</p>
     *
     * <p>Self-healing yaklaşımı: startup'ta tüm {@code market.bistIndex.history} girdileri
     * temizlenir, sonraki istek taze veriyi yeni sembol formatıyla doldurur. Gelecekteki
     * sembol/format değişiklikleri için manuel {@code redis-cli DEL} gerekmez.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void evictStaleCachesOnStartup() {
        clearBistCache("startup");
    }

    /**
     * BIST endeks cache'ini saatlik temizle.
     *
     * <p>Cache TTL'i zaten 60 dk olsa da; bu schedule (a) TTL drift'ine karşı koruma sağlar,
     * (b) silver/precious/commodity ile aynı kalıpta tutarlılık kazandırır, (c) Yahoo geçici
     * olarak boş yanıt verirse bir sonraki tetiklemede taze çekim garanti edilir.</p>
     */
    @Scheduled(initialDelay = 60_000, fixedRate = 3_600_000)
    public void evictBistIndexCaches() {
        clearBistCache("hourly");
    }

    /**
     * Sembol + tarih aralığı için BIST endeks Yahoo chart cevabını çeker.
     * Cache anahtarı: sembol + Yahoo range string (1mo / 1y / 5y …) — tarih bilgisi range
     * granularitysine yuvarlandığı için ardışık çağrılar aynı cache'i paylaşır.
     * Spring AOP burada DIŞARIDAN çağrı yoluyla devreye girer (controller → bean.fetchChart);
     * self-invocation yok.
     */
    @Cacheable(cacheNames = "market.bistIndex.history",
               key = "(#symbol == null ? '' : #symbol.toUpperCase()) + ':' + T(com.finance.portal.market.application.indicator.BistIndexService).pickYahooRange(#from, #to)")
    public Optional<YahooChartSnapshot> fetchChart(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null) {
            return Optional.empty();
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        String yahooSymbol = YAHOO_SYMBOLS.get(upper);
        if (yahooSymbol == null) {
            return Optional.empty();
        }
        String yahooRange = pickYahooRange(from, to);
        try {
            YahooChartSnapshot snap = yahooStockPort.fetchChartWithParams(yahooSymbol, yahooRange, "1d");
            // Defansif: Yahoo bazen 200 + boş quote döner (özellikle yanlış sembol formatlarında).
            // Bu durumu erken yakala ki sonraki katmanlarda "veri yok" şeklinde sessiz başarısızlık olmasın.
            if (snap == null
                    || snap.getTimestamps() == null
                    || snap.getTimestamps().isEmpty()
                    || snap.getQuote() == null
                    || snap.getQuote().getClose() == null
                    || snap.getQuote().getClose().isEmpty()) {
                log.warn("Yahoo BIST index returned empty payload [{}] range={}", yahooSymbol, yahooRange);
                return Optional.empty();
            }
            return Optional.of(snap);
        } catch (Exception ex) {
            log.warn("Yahoo BIST index fetch failed [{}]: {}", yahooSymbol, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tarih aralığını Yahoo'nun desteklediği range string'ine yuvarlar.
     * SpEL'den çağrılabilmesi için public-static.
     */
    public static String pickYahooRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "1mo";
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 7)    return "5d";
        if (days <= 35)   return "1mo";
        if (days <= 100)  return "3mo";
        if (days <= 200)  return "6mo";
        if (days <= 400)  return "1y";
        if (days <= 800)  return "2y";
        if (days <= 1900) return "5y";
        // Çok uzun pencere (örn. kıyas "Tüm" → ~10 yıl): Yahoo'nun verdiği TÜM günlük geçmişi iste.
        // Tek toplu çağrı (FX gibi gün-gün değil) — endekste mümkün olan en uzun seriyi getirir.
        return "max";
    }
}
