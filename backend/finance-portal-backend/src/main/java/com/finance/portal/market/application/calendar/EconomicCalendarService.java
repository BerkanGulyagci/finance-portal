package com.finance.portal.market.application.calendar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ekonomik takvim verilerini sağlar (TradingView backed).
 * Cache: {@code market.calendar} — TTL CacheConfig'de tanımlı (varsayılan 1 saat).
 *
 * <p><b>Dayanıklılık (LKG):</b> Kaynak (TradingView) çökerse/erişilemezse, {@link LastKnownGoodCache}
 * son başarılı takvim verisini servis eder (stale-if-error). Bu, dokümante olmayan bir endpoint'e
 * dayanmanın kırılganlığını telafi eder — TradingView değişse/kapansa bile UI boş kalmaz, entegrasyon
 * logu uyarı düşer (yönetici Grafana'dan görür). {@code @Cacheable} HIT'te LKG çağrılmaz (Redis hızlı
 * yol); yalnız cache MISS'te LKG fetch + backup yapar → ikinci savunma hattı.
 *
 * <p><b>Chunking:</b> Kaynak tek istekte sınırlı sayıda olay döner (cap). Geniş aralık (1 yıl) olduğu
 * gibi istendiğinde son kısım kesik gelebilir. Bu yüzden {@link #CHUNK_DAYS} günlük parçalara böl,
 * sırayla çek, (time, country, event, currency) tuple'ı ile dedup edip zamana göre sırala.
 */
@Service
public class EconomicCalendarService {

    private static final Logger log = LoggerFactory.getLogger(EconomicCalendarService.class);

    /** LKG anahtar öneki — namespace + tarih aralığı ile benzersiz. */
    private static final String LKG_PREFIX = "market.calendar:";

    /** LKG TTL — kaynak uzun süre çökse bile son veri 3 gün servis edilir (hızlı-değişmez veri). */
    private static final Duration LKG_TTL = Duration.ofDays(3);

    private static final TypeReference<List<EconomicCalendarEvent>> EVENT_LIST_TYPE =
            new TypeReference<>() {};

    /**
     * Tek chunk için maksimum gün sayısı.
     * Probe sonucu: 30 gün → ~2600 olay (tam aralık döner); 35 gün → 3000 olay döner
     * fakat son 5-6 gün KESİK gelir. Finnhub'ın gerçek cap'i ~3000 olay olduğu için
     * 30 günlük chunk'lar her aralığı eksiksiz kapsar.
     */
    private static final long CHUNK_DAYS = 30;

    /** Chunk'lar arası gecikme — free tier rate-limit (~60 req/dk) için güvenli aralık. */
    private static final long BACKOFF_MS = 100;

    /** Toplam istenebilecek maksimum aralık (gün). Daha büyük aralık kırpılır. */
    private static final long MAX_RANGE_DAYS = 400;

    private final EconomicCalendarPort port;
    private final LastKnownGoodCache lkg;

    public EconomicCalendarService(EconomicCalendarPort port, LastKnownGoodCache lkg) {
        this.port = port;
        this.lkg = lkg;
    }

    /**
     * Verilen aralıktaki tüm olayları döner.
     * {@code from} {@code to}'dan büyükse swap edilir; aralık {@link #MAX_RANGE_DAYS} ile sınırlanır.
     * &gt; {@link #CHUNK_DAYS} ise chunk'lara bölünerek çekilir; sonuçlar dedup + sırala.
     *
     * <p>Redis {@code @Cacheable} HIT → anında döner (LKG'ye uğramaz). MISS → {@link LastKnownGoodCache}
     * fetch'i sarar: kaynak çökerse son başarılı veri ({@code lkg:} anahtarı) servis edilir.
     */
    @Cacheable(cacheNames = "market.calendar", key = "#from.toString() + '_' + #to.toString()")
    public List<EconomicCalendarEvent> getEvents(LocalDate from, LocalDate to) {
        LocalDate start = from;
        LocalDate end = to;
        if (start.isAfter(end)) {
            LocalDate tmp = start; start = end; end = tmp;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_RANGE_DAYS) {
            log.warn("[Calendar] Aralık {} gün — {} güne kırpıldı (from={}, to={})",
                    days, MAX_RANGE_DAYS, start, end);
            end = start.plusDays(MAX_RANGE_DAYS);
            days = MAX_RANGE_DAYS;
        }

        final LocalDate fStart = start;
        final LocalDate fEnd = end;
        final long fDays = days;
        // LKG: kaynak çökerse son başarılı veriyi servis et (stale-if-error). Boş sonuç
        // (kaynak erişildi ama hiç olay yok) LKG'de saklanmaz → gerçek veri gelince güncellenir.
        return lkg.resilient(
                LKG_PREFIX + fStart + "_" + fEnd,
                LKG_TTL,
                EVENT_LIST_TYPE,
                () -> (fDays <= CHUNK_DAYS) ? port.fetch(fStart, fEnd) : fetchInChunks(fStart, fEnd));
    }

    private List<EconomicCalendarEvent> fetchInChunks(LocalDate start, LocalDate end) {
        long totalDays = ChronoUnit.DAYS.between(start, end);
        long startMs = System.currentTimeMillis();

        // Dedup için (time + country + event + currency) tuple
        Set<EventKey> seen = new HashSet<>();
        List<EconomicCalendarEvent> merged = new ArrayList<>();

        LocalDate chunkStart = start;
        int chunkIdx = 0;
        int totalChunks = (int) Math.ceil((double) totalDays / CHUNK_DAYS);
        int failedChunks = 0;

        while (!chunkStart.isAfter(end)) {
            LocalDate chunkEnd = chunkStart.plusDays(CHUNK_DAYS - 1);
            if (chunkEnd.isAfter(end)) chunkEnd = end;
            chunkIdx++;

            try {
                long t0 = System.currentTimeMillis();
                List<EconomicCalendarEvent> chunk = port.fetch(chunkStart, chunkEnd);
                int before = merged.size();
                for (EconomicCalendarEvent ev : chunk) {
                    if (seen.add(EventKey.of(ev))) {
                        merged.add(ev);
                    }
                }
                log.debug("[Calendar] chunk {}/{} ({} → {}): {} olay, +{} yeni — {} ms",
                        chunkIdx, totalChunks, chunkStart, chunkEnd, chunk.size(),
                        merged.size() - before, System.currentTimeMillis() - t0);
            } catch (Exception e) {
                failedChunks++;
                log.warn("[Calendar] chunk {}/{} ({} → {}) hata: {}",
                        chunkIdx, totalChunks, chunkStart, chunkEnd, e.getMessage());
            }

            chunkStart = chunkEnd.plusDays(1);
            if (!chunkStart.isAfter(end)) {
                try {
                    Thread.sleep(BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[Calendar] chunking interrupted");
                    break;
                }
            }
        }

        // Zamana göre artan sırala
        merged.sort(Comparator.comparing((EconomicCalendarEvent ev) -> ev.getTime(),
                Comparator.nullsLast(Comparator.naturalOrder())));

        log.info("[Calendar] {} → {} ({} gün): {} olay ({} chunk, {} başarısız) — {} ms",
                start, end, totalDays, merged.size(), totalChunks, failedChunks,
                System.currentTimeMillis() - startMs);
        return merged;
    }

    /** Dedup için kompozit anahtar — chunk sınırlarında tekrarlayabilen olayları teke indirir. */
    private record EventKey(String time, String country, String event, String currency) {
        static EventKey of(EconomicCalendarEvent ev) {
            return new EventKey(
                    Objects.toString(ev.getTime(), ""),
                    Objects.toString(ev.getCountry(), ""),
                    Objects.toString(ev.getEvent(), ""),
                    Objects.toString(ev.getCurrency(), "")
            );
        }
    }
}
