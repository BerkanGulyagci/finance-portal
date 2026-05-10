package com.finance.portal.market.application.silver;

import com.finance.portal.market.infrastructure.external.precious.BistMetalDailyPoint;
import com.finance.portal.market.infrastructure.external.precious.BistMetalFiyatlariClient;
import com.finance.portal.market.infrastructure.external.precious.BistPreciousMetalPoint;
import com.finance.portal.market.infrastructure.external.precious.BistPreciousMetalsClient;
import com.finance.portal.market.infrastructure.external.precious.PreciousMetalType;
import com.finance.portal.market.infrastructure.external.precious.PriceUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Gümüş piyasa servisi.
 * Kaynak: Borsa İstanbul Kıymetli Madenler (pazart=SG).
 *
 * <p>Veri kalitesi notu: Bazı günlerde kpo=0 ve sum_o=0 gelebilir.
 * Bu kayıtlar validPrice=false olarak işaretlenir ve filtrelenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SilverMarketService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String DISCLAIMER =
            "Bu fiyatlar Borsa İstanbul resmi metal fiyatından alınan referans değerlerdir. " +
            "Serbest piyasa alış/satış ve makas dahil değildir.";

    private final BistPreciousMetalsClient bistClient;
    private final BistMetalFiyatlariClient metalClient;

    // ── Cache temizleme — her saat ────────────────────────────────────────────

    @Scheduled(initialDelay = 60_000, fixedRate = 3_600_000)
    @CacheEvict(cacheNames = {"market.silver.spot", "market.silver.history"}, allEntries = true)
    public void evictSilverCaches() {
        log.info("Silver caches evicted (hourly)");
    }

    // ── Spot ─────────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.silver.spot", key = "'spot'")
    public SilverSpotResponse getSpotSilver() {
        // TL/Kg — en son geçerli nokta
        BistPreciousMetalPoint latestTry = bistClient.fetchLatestValidPoint(
                PreciousMetalType.SILVER, PriceUnit.TRY_KG);

        // USD/Ons — en son geçerli nokta
        BistPreciousMetalPoint latestUsd = bistClient.fetchLatestValidPoint(
                PreciousMetalType.SILVER, PriceUnit.USD_ONS);

        if (latestTry == null && latestUsd == null) {
            log.warn("BIST silver spot unavailable");
            SilverSpotResponse empty = new SilverSpotResponse();
            empty.setSource("Borsa İstanbul");
            empty.setOfficial(true);
            empty.setFallback(false);
            empty.setStale(true);
            empty.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
            empty.setDisclaimer(DISCLAIMER);
            return empty;
        }

        SilverSpotResponse resp = new SilverSpotResponse();
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setStale(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);

        if (latestTry != null) {
            resp.setBistDate(latestTry.getDate());
            resp.setLastValidDate(latestTry.getDate());
            resp.setCloseTryKg(latestTry.getCloseRaw());
            resp.setWeightedAverageTryKg(latestTry.getWeightedAverageRaw());
            resp.setLowTryKg(latestTry.getLowRaw());
            resp.setHighTryKg(latestTry.getHighRaw());
            resp.setVolumeTry(latestTry.getVolumeRaw());
            resp.setQuantityKg(latestTry.getQuantityKg());
            resp.setTransactionCount(latestTry.getTransactionCount());
            resp.setSilverGramTry(latestTry.getGramWeightedAverage());
            resp.setSilverGramCloseTry(latestTry.getGramClose());
            resp.setSilverGramLowTry(latestTry.getGramLow());
            resp.setSilverGramHighTry(latestTry.getGramHigh());
        }

        if (latestUsd != null) {
            resp.setSilverUsdOns(latestUsd.getCloseUsdOns());
            resp.setSilverUsdOnsHigh(latestUsd.getHighUsdOns());
            resp.setSilverUsdOnsLow(latestUsd.getLowUsdOns());
            resp.setSilverUsdOnsWeightedAverage(latestUsd.getWeightedAverageUsdOns());
        }

        return resp;
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.silver.history", key = "#range + ':' + #currency")
    public SilverHistoryResponse getSilverHistory(String range, String currency) {
        boolean isTry = !"USD".equalsIgnoreCase(currency);
        String normalizedRange = range == null ? "1M" : range.toUpperCase();

        // ALL ve 5Y için metal-fiyatlari (AG) endpoint'i kullan — 2011'den veri var
        boolean useMetalRef = "ALL".equals(normalizedRange) || "5Y".equals(normalizedRange);

        if (useMetalRef) {
            return getSilverHistoryFromMetalRef(normalizedRange, isTry ? "TRY" : "USD");
        }

        return isTry
                ? getSilverHistoryTry(normalizedRange)
                : getSilverHistoryUsd(normalizedRange);
    }

    // ── AG metal-fiyatlari history (ALL / 5Y) ─────────────────────────────────

    private SilverHistoryResponse getSilverHistoryFromMetalRef(String range, String currency) {
        String[] dates = rangeToBistDates(range);
        List<BistMetalDailyPoint> raw = metalClient.fetchMetalPrices(
                PreciousMetalType.SILVER, dates[0], dates[1]);

        List<BistMetalDailyPoint> valid = raw.stream()
                .filter(BistMetalDailyPoint::isValidPrice)
                .toList();

        log.info("Silver AG ref history [{}] {}: {} raw, {} valid [{} → {}]",
                currency, range, raw.size(), valid.size(), dates[0], dates[1]);

        List<SilverHistoryPoint> points = new ArrayList<>(valid.size());
        BigDecimal prevClose = null;

        for (BistMetalDailyPoint bp : valid) {
            SilverHistoryPoint pt = new SilverHistoryPoint();
            pt.setDate(bp.getDate());

            if ("USD".equals(currency)) {
                BigDecimal close = bp.getUsdOns();
                pt.setClose(close);
                pt.setOpen(prevClose != null ? prevClose : close);
                pt.setHigh(close);
                pt.setLow(close);
                pt.setWeightedAverageUsdOns(bp.getUsdOns());
                prevClose = close;
            } else {
                BigDecimal gramClose = bp.getTryGram();
                pt.setClose(gramClose);
                pt.setOpen(prevClose != null ? prevClose : gramClose);
                pt.setHigh(gramClose);
                pt.setLow(gramClose);
                pt.setWeightedAverage(gramClose);
                pt.setCloseTryKg(bp.getTryKg());
                pt.setWeightedAverageTryKg(bp.getTryKg());
                prevClose = gramClose;
            }
            points.add(pt);
        }

        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setSymbol("BIST/GUMUS-REF");
        resp.setRange(range);
        resp.setCurrency(currency);
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setStale(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);
        resp.setPoints(points);
        return resp;
    }

    private SilverHistoryResponse getSilverHistoryTry(String range) {
        String[] dates = rangeToBistDates(range);

        // 1. veri-sorgulama endpoint'inden OHLC verisi (mum grafik için gerçek high/low)
        List<BistPreciousMetalPoint> raw =
                bistClient.fetchHistory(PreciousMetalType.SILVER, PriceUnit.TRY_KG, dates[0], dates[1]);

        // 2. AG metal-fiyatlari endpoint'inden referans fiyatlar (eksik günleri tamamlamak için)
        List<BistMetalDailyPoint> agRef =
                metalClient.fetchMetalPrices(PreciousMetalType.SILVER, dates[0], dates[1]);

        // AG referans map: tarih → nokta
        java.util.Map<String, BistMetalDailyPoint> agByDate = new java.util.LinkedHashMap<>();
        agRef.stream().filter(BistMetalDailyPoint::isValidPrice)
             .forEach(p -> agByDate.put(p.getDate(), p));

        // veri-sorgulama geçerli kayıtları map
        java.util.Map<String, BistPreciousMetalPoint> vstByDate = new java.util.LinkedHashMap<>();
        raw.stream().filter(BistPreciousMetalPoint::isValidPrice)
           .forEach(p -> vstByDate.put(p.getDate(), p));

        // Tüm tarihleri sıralı birleştir
        java.util.Set<String> allDates = new java.util.TreeSet<>();
        vstByDate.keySet().forEach(allDates::add);
        agByDate.keySet().forEach(allDates::add);

        List<SilverHistoryPoint> points = new ArrayList<>();
        BigDecimal prevClose = null;

        for (String date : allDates) {
            BistPreciousMetalPoint vst = vstByDate.get(date);
            BistMetalDailyPoint ag   = agByDate.get(date);

            SilverHistoryPoint pt = new SilverHistoryPoint();
            pt.setDate(date);

            if (vst != null) {
                // veri-sorgulama'dan OHLC — mum grafik için gerçek high/low
                pt.setClose(vst.getGramClose());
                pt.setHigh(vst.getGramHigh());
                pt.setLow(vst.getGramLow());
                pt.setWeightedAverage(vst.getGramWeightedAverage());
                pt.setCloseTryKg(vst.getCloseRaw());
                pt.setWeightedAverageTryKg(vst.getWeightedAverageRaw());
                pt.setOpen(prevClose != null ? prevClose : vst.getGramClose());
                prevClose = vst.getGramClose();
                if (vst.getQuantityKg() != null) {
                    pt.setVolume(vst.getQuantityKg().multiply(BigDecimal.valueOf(1000)).longValue());
                }
            } else if (ag != null) {
                // AG referans — eksik gün, sadece referans fiyat (high=low=close)
                BigDecimal gramClose = ag.getTryGram();
                pt.setClose(gramClose);
                pt.setHigh(gramClose);
                pt.setLow(gramClose);
                pt.setWeightedAverage(gramClose);
                pt.setCloseTryKg(ag.getTryKg());
                pt.setWeightedAverageTryKg(ag.getTryKg());
                pt.setOpen(prevClose != null ? prevClose : gramClose);
                prevClose = gramClose;
            } else {
                continue;
            }
            points.add(pt);
        }

        log.info("Silver TRY hybrid history: {} vst, {} ag, {} merged [{} → {}]",
                vstByDate.size(), agByDate.size(), points.size(), dates[0], dates[1]);

        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setSymbol("BIST/GUMUS");
        resp.setRange(range);
        resp.setCurrency("TRY");
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setStale(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);
        resp.setPoints(points);
        return resp;
    }

    private SilverHistoryResponse getSilverHistoryUsd(String range) {
        String[] dates = rangeToBistDates(range);
        List<BistPreciousMetalPoint> raw =
                bistClient.fetchHistory(PreciousMetalType.SILVER, PriceUnit.USD_ONS, dates[0], dates[1]);

        List<BistPreciousMetalPoint> valid = raw.stream()
                .filter(BistPreciousMetalPoint::isValidPrice)
                .toList();

        log.info("Silver USD history: {} raw, {} valid [{} → {}]",
                raw.size(), valid.size(), dates[0], dates[1]);

        List<SilverHistoryPoint> points = new ArrayList<>(valid.size());
        BigDecimal prevClose = null;

        for (BistPreciousMetalPoint bp : valid) {
            SilverHistoryPoint pt = new SilverHistoryPoint();
            pt.setDate(bp.getDate());
            pt.setClose(bp.getCloseUsdOns());
            pt.setHigh(bp.getHighUsdOns());
            pt.setLow(bp.getLowUsdOns());
            pt.setWeightedAverageUsdOns(bp.getWeightedAverageUsdOns());
            pt.setOpen(prevClose != null ? prevClose : bp.getCloseUsdOns());
            prevClose = bp.getCloseUsdOns();
            if (bp.getQuantityKg() != null) {
                pt.setVolume(bp.getQuantityKg().multiply(BigDecimal.valueOf(1000)).longValue());
            }
            points.add(pt);
        }

        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setSymbol("BIST/GUMUS-ONS");
        resp.setRange(range);
        resp.setCurrency("USD");
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setStale(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(
                "Mum grafikte açılış değeri BIST tarafından verilmediği için " +
                "önceki gün kapanışından sentetik olarak üretilmiştir.");
        resp.setPoints(points);
        return resp;
    }

    // ── Range helper ──────────────────────────────────────────────────────────

    private String[] rangeToBistDates(String range) {
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
