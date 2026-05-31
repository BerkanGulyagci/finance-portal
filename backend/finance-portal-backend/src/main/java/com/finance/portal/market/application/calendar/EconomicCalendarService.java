package com.finance.portal.market.application.calendar;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ekonomik takvim verilerini sağlar (Finnhub backed).
 * Cache: {@code market.calendar} — TTL CacheConfig'de tanımlı (varsayılan 1 saat).
 *
 * <p><b>Chunking:</b> Finnhub {@code /calendar/economic} tek istekte ~3000 olay döner
 * (sessiz cap). 1 yıllık aralık olduğu gibi istendiğinde yalnız ilk ~33 günü görünür.
 * Bu yüzden {@link #CHUNK_DAYS} günlük (90 gün) parçalara böl, sırayla çek,
 * (time, country, event, currency) tuple'ı ile dedup edip zamana göre sırala.
 */
@Service
public class EconomicCalendarService {

    private static final Logger log = LoggerFactory.getLogger(EconomicCalendarService.class);

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

    public EconomicCalendarService(EconomicCalendarPort port) {
        this.port = port;
    }

    /**
     * Verilen aralıktaki tüm olayları döner.
     * {@code from} {@code to}'dan büyükse swap edilir; aralık {@link #MAX_RANGE_DAYS} ile sınırlanır.
     * &gt; {@link #CHUNK_DAYS} ise chunk'lara bölünerek çekilir; sonuçlar dedup + sırala.
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

        // ≤ 90 gün: tek çağrı (geriye uyum + en az gecikme).
        if (days <= CHUNK_DAYS) {
            return port.fetch(start, end);
        }

        // > 90 gün: chunk'la.
        return fetchInChunks(start, end);
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
