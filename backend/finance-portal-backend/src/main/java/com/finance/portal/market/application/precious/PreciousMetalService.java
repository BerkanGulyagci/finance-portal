package com.finance.portal.market.application.precious;

import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Kıymetli Madenler referans fiyat servisi.
 * Kaynak: BIST metal-fiyatlari.php (priceRef=MTL)
 * Desteklenen metaller: AU (Altın), AG (Gümüş), PT (Platin), PD (Paladyum)
 *
 * NOT: Altın ve Gümüş için OHLC grafik hâlâ veri-sorgulama endpoint'inden beslenir.
 * Bu servis sadece referans fiyat (line chart) ve karşılaştırma için kullanılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreciousMetalService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String DISCLAIMER =
            "Bu fiyatlar Borsa İstanbul resmi metal fiyatından alınan referans değerlerdir. " +
            "Serbest piyasa alış/satış ve makas dahil değildir.";

    private final BistMetalFiyatlariPort metalClient;

    // ── Cache temizleme ───────────────────────────────────────────────────────

    @Scheduled(initialDelay = 120_000, fixedRate = 3_600_000)
    @SchedulerLock(name = "precious-cache-evict", lockAtMostFor = "PT1H", lockAtLeastFor = "PT55M")
    @CacheEvict(cacheNames = {"market.precious.spot", "market.precious.history"}, allEntries = true)
    public void evictPreciousCaches() {
        log.info("Precious metals (PT/PD) caches evicted (hourly)");
    }

    // ── Spot ─────────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.precious.spot", key = "#metal.name()")
    public PreciousMetalSpotResponse getSpot(PreciousMetalType metal) {
        BistMetalDailyPoint latest = metalClient.fetchLatestValidPoint(metal);

        PreciousMetalSpotResponse resp = new PreciousMetalSpotResponse();
        resp.setMetalType(metal);
        resp.setMetalName(metal.getDisplayName());
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);
        resp.setDataSourceType("BIST_METAL_FIYATLARI");

        if (latest == null) {
            log.warn("BIST {} spot unavailable", metal.getDisplayName());
            resp.setStale(true);
            return resp;
        }

        resp.setStale(false);
        resp.setLastValidDate(latest.getDate());
        resp.setUsdOns(latest.getUsdOns());
        resp.setTryKg(latest.getTryKg());
        resp.setTryGram(latest.getTryGram());
        resp.setEurOns(latest.getEurOns());
        return resp;
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.precious.history", key = "#metal.name() + ':' + #range + ':' + #currency")
    public PreciousMetalHistoryResponse getHistory(
            PreciousMetalType metal, String range, String currency) {

        String[] dates = rangeToDates(range);
        List<BistMetalDailyPoint> raw = metalClient.fetchMetalPrices(metal, dates[0], dates[1]);

        // validPrice filtresi
        List<BistMetalDailyPoint> valid = raw.stream()
                .filter(BistMetalDailyPoint::isValidPrice)
                .toList();

        log.info("{} history [{}] {}: {} raw, {} valid [{} → {}]",
                metal.getDisplayName(), currency, range,
                raw.size(), valid.size(), dates[0], dates[1]);

        List<PreciousMetalHistoryPoint> points = new ArrayList<>(valid.size());
        for (BistMetalDailyPoint bp : valid) {
            PreciousMetalHistoryPoint pt = new PreciousMetalHistoryPoint();
            pt.setDate(bp.getDate());
            pt.setTryGram(bp.getTryGram());
            pt.setTryKg(bp.getTryKg());
            pt.setUsdOns(bp.getUsdOns());
            pt.setEurOns(bp.getEurOns());

            // Seçilen currency'ye göre value alanını doldur
            BigDecimal val = switch (currency == null ? "TRY" : currency.toUpperCase()) {
                case "USD" -> bp.getUsdOns();
                case "EUR" -> bp.getEurOns();
                default    -> bp.getTryGram(); // TRY → gram
            };
            pt.setValue(val);
            points.add(pt);
        }

        PreciousMetalHistoryResponse resp = new PreciousMetalHistoryResponse();
        resp.setMetalType(metal);
        resp.setMetalName(metal.getDisplayName());
        resp.setRange(range);
        resp.setCurrency(currency != null ? currency.toUpperCase() : "TRY");
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);
        resp.setDataSourceType("BIST_METAL_FIYATLARI");
        resp.setPoints(points);
        return resp;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String[] rangeToDates(String range) {
        LocalDate today = LocalDate.now(ISTANBUL);
        LocalDate start = switch (range == null ? "1M" : range.toUpperCase()) {
            case "1D"  -> today.minusDays(5);
            case "1W"  -> today.minusDays(10);
            case "3M"  -> today.minusMonths(3);
            case "1Y"  -> today.minusYears(1);
            case "5Y"  -> today.minusYears(5);
            case "ALL" -> LocalDate.of(2011, 1, 1);
            default    -> today.minusMonths(1);
        };
        return new String[]{start.format(DATE_FMT), today.format(DATE_FMT)};
    }
}
