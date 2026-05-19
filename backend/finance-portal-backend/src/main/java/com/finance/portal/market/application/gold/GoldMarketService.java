package com.finance.portal.market.application.gold;

import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import com.finance.portal.market.application.precious.port.BistPreciousMetalsPort;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Altın piyasa servisi.
 *
 * <b>Kaynak hiyerarşisi:</b>
 * <ol>
 *   <li>Borsa İstanbul Kıymetli Madenler (birincil, resmi)</li>
 *   <li>Yahoo Finance GC=F (fallback — sadece BIST erişilemezse)</li>
 * </ol>
 *
 * <b>canlialtinfiyatlari.com scraping kaldırıldı.</b>
 * GoldScraper bean'i hâlâ Spring context'te var ama bu servis tarafından kullanılmıyor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoldMarketService {

    // ── Sabitler ──────────────────────────────────────────────────────────────

    private static final String GOLD_SYMBOL = "GC=F";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 1 troy ons = 31.1035 gram */
    private static final BigDecimal TROY_OZ_TO_GRAM = new BigDecimal("31.1035");

    /**
     * Teorik fiyat hesaplama sabitleri.
     * 22 ayar için 0.9166, 14 ayar için 0.5850 kullanılır (22/24 veya 14/24 değil).
     */
    private static final BigDecimal FINENESS_22K = new BigDecimal("0.9166");
    private static final BigDecimal FINENESS_14K = new BigDecimal("0.5850");

    private static final BigDecimal GROSS_WEIGHT_QUARTER  = new BigDecimal("1.754");
    private static final BigDecimal GROSS_WEIGHT_HALF     = new BigDecimal("3.508");
    private static final BigDecimal GROSS_WEIGHT_ZIYNET   = new BigDecimal("7.016");
    private static final BigDecimal GROSS_WEIGHT_REPUBLIC = new BigDecimal("7.216");

    private static final String DISCLAIMER =
            "Bu fiyatlar Borsa İstanbul resmi metal fiyatından hesaplanan teorik referans " +
            "değerlerdir. Serbest piyasa alış/satış, işçilik, basım primi ve makas dahil değildir.";

    // ── Bağımlılıklar ─────────────────────────────────────────────────────────

    private final BistPreciousMetalsPort bistClient;
    private final BistMetalFiyatlariPort metalClient;
    private final YahooStockPort yahooStockPort;
    private final MarketFxService marketFxService;

    // ── Cache temizleme — her saat ────────────────────────────────────────────

    @Scheduled(initialDelay = 0, fixedRate = 3_600_000)
    @CacheEvict(cacheNames = {"market.gold.spot", "market.gold.history"}, allEntries = true)
    public void evictGoldCaches() {
        log.info("Gold caches evicted (hourly)");
    }

    // ── Spot ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/gold/spot
     *
     * BIST'ten son işlem günü verisi alınır, teorik türev fiyatlar hesaplanır.
     * BIST erişilemezse Yahoo GC=F fallback devreye girer.
     */
    @Cacheable(cacheNames = "market.gold.spot", key = "'spot'")
    public GoldSpotResponse getSpotGold() {
        // 1. BIST TL/Kg → gram referans
        BistPreciousMetalPoint latestGram = bistClient.fetchLatestValidPoint(
                PreciousMetalType.GOLD, PriceUnit.TRY_KG);

        if (latestGram != null && latestGram.getGramWeightedAverageTry() != null) {
            GoldSpotResponse resp = buildSpotFromBist(latestGram);

            // 2. BIST USD/Ons → ons spot
            BistPreciousMetalPoint latestOns = bistClient.fetchLatestValidPoint(
                    PreciousMetalType.GOLD, PriceUnit.USD_ONS);
            if (latestOns != null && latestOns.getCloseUsdOns() != null) {
                enrichWithBistOns(resp, latestOns);
            } else {
                enrichWithYahooOns(resp);
            }
            finalizeGoldSpotForTry(resp);
            return resp;
        }

        log.warn("BIST spot unavailable, falling back to Yahoo GC=F");
        GoldSpotResponse fallback = buildSpotFromYahooFallback();
        finalizeGoldSpotForTry(fallback);
        return fallback;
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * GET /api/gold/history?range=&currency=
     *
     * currency=TRY → BIST f_tipit=L/K (gram TL), Yahoo fallback
     * currency=USD → BIST f_tipit=$/O (ons USD), Yahoo fallback
     */
    @Cacheable(cacheNames = "market.gold.history", key = "#range + ':' + #currency")
    public GoldHistoryResponse getGoldHistory(String range, String currency) {
        boolean isTry = !"USD".equalsIgnoreCase(currency);
        String normalizedRange = range == null ? "1M" : range.toUpperCase();

        // ALL ve 5Y için metal-fiyatlari (AU) endpoint'i kullan — 2011'den veri var
        // Diğer range'ler için veri-sorgulama (OHLC destekli)
        boolean useMetalRef = "ALL".equals(normalizedRange) || "5Y".equals(normalizedRange);

        if (useMetalRef) {
            return getGoldHistoryFromMetalRef(normalizedRange, isTry ? "TRY" : "USD");
        }

        return isTry
                ? getGoldHistoryGramFromBist(normalizedRange)
                : getGoldHistoryOnsFromBist(normalizedRange);
    }

    // ── AU metal-fiyatlari history (ALL / 5Y) ─────────────────────────────────

    private GoldHistoryResponse getGoldHistoryFromMetalRef(String range, String currency) {
        String[] dates = rangeToBistDates(range);
        List<BistMetalDailyPoint> raw = metalClient.fetchMetalPrices(
                PreciousMetalType.GOLD, dates[0], dates[1]);

        List<BistMetalDailyPoint> valid = raw.stream()
                .filter(BistMetalDailyPoint::isValidPrice)
                .toList();

        log.info("Gold AU ref history [{}] {}: {} raw, {} valid [{} → {}]",
                currency, range, raw.size(), valid.size(), dates[0], dates[1]);

        List<GoldHistoryPoint> points = new ArrayList<>(valid.size());
        BigDecimal prevClose = null;

        for (BistMetalDailyPoint bp : valid) {
            GoldHistoryPoint pt = new GoldHistoryPoint();
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

        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setSymbol("BIST/ALTIN-REF");
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

    // ── BIST gram (TL/Kg) history ─────────────────────────────────────────────

    private GoldHistoryResponse getGoldHistoryGramFromBist(String range) {
        String[] dates = rangeToBistDates(range);
        List<BistPreciousMetalPoint> bistPoints =
                bistClient.fetchHistory(PreciousMetalType.GOLD, PriceUnit.TRY_KG, dates[0], dates[1]);
        // validPrice filtresi
        bistPoints = bistPoints.stream().filter(BistPreciousMetalPoint::isValidPrice).toList();

        if (!bistPoints.isEmpty()) {
            return buildHistoryFromBistGram(bistPoints, range);
        }

        log.warn("BIST gram history empty for range={}, falling back to Yahoo", range);
        GoldHistoryResponse fallback = getGoldHistoryFromYahoo(range, "TRY");
        fallback.setFallback(true);
        fallback.setSource("Yahoo Finance Fallback");
        fallback.setOfficial(false);
        return fallback;
    }

    private GoldHistoryResponse buildHistoryFromBistGram(
            List<BistPreciousMetalPoint> bistPoints, String range) {

        List<GoldHistoryPoint> points = new ArrayList<>(bistPoints.size());
        BigDecimal prevClose = null;

        for (BistPreciousMetalPoint bp : bistPoints) {
            GoldHistoryPoint pt = new GoldHistoryPoint();
            pt.setDate(bp.getDate());
            pt.setClose(bp.getGramClose());
            pt.setHigh(bp.getGramHigh());
            pt.setLow(bp.getGramLow());
            pt.setWeightedAverage(bp.getGramWeightedAverage());
            pt.setCloseTryKg(bp.getCloseRaw());
            pt.setWeightedAverageTryKg(bp.getWeightedAverageRaw());
            pt.setOpen(prevClose != null ? prevClose : bp.getGramClose());
            prevClose = bp.getGramClose();
            if (bp.getQuantityKg() != null) {
                pt.setVolume(bp.getQuantityKg().multiply(BigDecimal.valueOf(1000)).longValue());
            }
            points.add(pt);
        }

        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setSymbol("BIST/ALTIN");
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

    // ── BIST ons (USD/Ons) history ────────────────────────────────────────────

    private GoldHistoryResponse getGoldHistoryOnsFromBist(String range) {
        String[] dates = rangeToBistDates(range);
        List<BistPreciousMetalPoint> bistPoints =
                bistClient.fetchHistory(PreciousMetalType.GOLD, PriceUnit.USD_ONS, dates[0], dates[1]);
        bistPoints = bistPoints.stream().filter(BistPreciousMetalPoint::isValidPrice).toList();

        if (!bistPoints.isEmpty()) {
            return buildHistoryFromBistOns(bistPoints, range);
        }

        log.warn("BIST ons history empty for range={}, falling back to Yahoo", range);
        return getGoldHistoryFromYahoo(range, "USD");
    }

    private GoldHistoryResponse buildHistoryFromBistOns(
            List<BistPreciousMetalPoint> bistPoints, String range) {

        List<GoldHistoryPoint> points = new ArrayList<>(bistPoints.size());
        BigDecimal prevClose = null;

        for (BistPreciousMetalPoint bp : bistPoints) {
            GoldHistoryPoint pt = new GoldHistoryPoint();
            pt.setDate(bp.getDate());
            pt.setClose(bp.getCloseUsdOns());
            pt.setHigh(bp.getHighUsdOns());
            pt.setLow(bp.getLowUsdOns());
            pt.setWeightedAverageUsdOns(bp.getWeightedAverageUsdOns());
            pt.setVolumeUsd(bp.getVolumeUsd());
            pt.setQuantityKg(bp.getQuantityKg());
            pt.setOpen(prevClose != null ? prevClose : bp.getCloseUsdOns());
            prevClose = bp.getCloseUsdOns();
            if (bp.getQuantityKg() != null) {
                pt.setVolume(bp.getQuantityKg().multiply(BigDecimal.valueOf(1000)).longValue());
            }
            points.add(pt);
        }

        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setSymbol("BIST/ALTIN-ONS");
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

    // ── Yahoo history (ONS/USD veya fallback) ─────────────────────────────────

    private GoldHistoryResponse getGoldHistoryFromYahoo(String range, String currency) {
        try {
            String[] params = rangeToYahooParams(range);
            String yahooRange = params[0];
            String interval   = params[1];

            YahooChartSnapshot goldChart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, yahooRange, interval);
            List<Long> timestamps = goldChart.getTimestamps();
            List<BigDecimal> closes = null, opens = null, highs = null, lows = null;
            List<Long> volumes = null;

            YahooQuoteSeries q = goldChart.getQuote();
            if (q != null) {
                closes = q.getClose();
                opens = q.getOpen();
                highs = q.getHigh();
                lows = q.getLow();
                volumes = q.getVolume();
            }

            List<GoldHistoryPoint> points = new ArrayList<>();
            int size = timestamps != null ? timestamps.size() : 0;

            for (int i = 0; i < size; i++) {
                Long ts = timestamps.get(i);
                BigDecimal close = closes != null && i < closes.size() ? closes.get(i) : null;
                if (ts == null || close == null) continue;

                String date = Instant.ofEpochSecond(ts).atZone(ISTANBUL).toLocalDate().format(DATE_FMT);

                GoldHistoryPoint pt = new GoldHistoryPoint();
                pt.setDate(date);
                pt.setClose(close.setScale(2, RoundingMode.HALF_UP));
                if (opens  != null && i < opens.size()  && opens.get(i)  != null) pt.setOpen(opens.get(i).setScale(2, RoundingMode.HALF_UP));
                if (highs  != null && i < highs.size()  && highs.get(i)  != null) pt.setHigh(highs.get(i).setScale(2, RoundingMode.HALF_UP));
                if (lows   != null && i < lows.size()   && lows.get(i)   != null) pt.setLow(lows.get(i).setScale(2, RoundingMode.HALF_UP));
                if (volumes != null && i < volumes.size()) pt.setVolume(volumes.get(i));
                points.add(pt);
            }

            GoldHistoryResponse resp = new GoldHistoryResponse();
            resp.setSymbol(GOLD_SYMBOL);
            resp.setRange(range);
            resp.setCurrency("USD".equalsIgnoreCase(currency) ? "USD" : "TRY");
            resp.setSource("Yahoo Finance Fallback");
            resp.setOfficial(false);
            resp.setFallback(true);
            resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
            resp.setPoints(points);
            return resp;

        } catch (Exception e) {
            log.error("Yahoo gold history failed for range={}: {}", range, e.getMessage());
            GoldHistoryResponse empty = new GoldHistoryResponse();
            empty.setRange(range);
            empty.setCurrency(currency);
            empty.setSource("Yahoo Finance Fallback");
            empty.setFallback(true);
            empty.setPoints(new ArrayList<>());
            return empty;
        }
    }

    // ── Spot builders ─────────────────────────────────────────────────────────

    private GoldSpotResponse buildSpotFromBist(BistPreciousMetalPoint latest) {
        BigDecimal officialGramTry = latest.getGramWeightedAverage();

        GoldSpotResponse resp = new GoldSpotResponse();
        resp.setSource("Borsa İstanbul");
        resp.setOfficial(true);
        resp.setFallback(false);
        resp.setStale(false);
        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setDisclaimer(DISCLAIMER);
        resp.setBistDate(latest.getDate());

        // BIST ham değerler
        resp.setOfficialPureGoldGramTry(officialGramTry);
        resp.setGramCloseTry(latest.getGramClose());
        resp.setGramLowTry(latest.getGramLow());
        resp.setGramHighTry(latest.getGramHigh());
        resp.setVolumeTry(latest.getVolumeRaw());
        resp.setQuantityKg(latest.getQuantityKg());
        resp.setTransactionCount(latest.getTransactionCount());

        // Teorik türev fiyatlar
        BigDecimal gramGold     = officialGramTry;
        BigDecimal quarterGold  = officialGramTry.multiply(GROSS_WEIGHT_QUARTER).multiply(FINENESS_22K).setScale(2, RoundingMode.HALF_UP);
        BigDecimal halfGold     = officialGramTry.multiply(GROSS_WEIGHT_HALF).multiply(FINENESS_22K).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ziynetGold   = officialGramTry.multiply(GROSS_WEIGHT_ZIYNET).multiply(FINENESS_22K).setScale(2, RoundingMode.HALF_UP);
        BigDecimal republicGold = officialGramTry.multiply(GROSS_WEIGHT_REPUBLIC).multiply(FINENESS_22K).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bracelet14k  = officialGramTry.multiply(FINENESS_14K).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bracelet22k  = officialGramTry.multiply(FINENESS_22K).setScale(2, RoundingMode.HALF_UP);

        resp.setGramGoldTry(gramGold.setScale(2, RoundingMode.HALF_UP));
        resp.setQuarterGoldTry(quarterGold);
        resp.setHalfGoldTry(halfGold);
        resp.setZiynetGoldTry(ziynetGold);
        resp.setRepublicGoldTry(republicGold);
        resp.setFourteenKBraceletTry(bracelet14k);
        resp.setTwentyTwoKBraceletTry(bracelet22k);

        // Geriye dönük uyumluluk — eski frontend alanları
        resp.setGramTl(gramGold.setScale(2, RoundingMode.HALF_UP));
        resp.setCeyrekTl(quarterGold);
        resp.setYarimTl(halfGold);
        resp.setTamTl(ziynetGold);
        resp.setCumhuriyetTl(republicGold);
        resp.setAyar14Tl(bracelet14k);
        resp.setAyar22Tl(bracelet22k);
        resp.setUpdatedAt(LocalDateTime.now(ISTANBUL).toString());

        // ONS — Yahoo'dan çek (fallback olarak)
        enrichWithYahooOns(resp);

        return resp;
    }

    private GoldSpotResponse buildSpotFromYahooFallback() {
        GoldSpotResponse resp = new GoldSpotResponse();
        resp.setSource("Yahoo Finance Fallback");
        resp.setOfficial(false);
        resp.setFallback(true);
        resp.setDisclaimer("BIST verisi alınamadığı için Yahoo Finance GC=F fallback verisi gösteriliyor.");

        try {
            YahooChartSnapshot chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
            YahooStockMeta meta = chart.getMeta();

            BigDecimal onsUsd = safe(meta.getRegularMarketPrice());
            BigDecimal prevClose = safe(meta.getPreviousClose());
            BigDecimal changePercent = BigDecimal.ZERO;
            if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                changePercent = onsUsd.subtract(prevClose)
                        .divide(prevClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            resp.setOnsUsd(onsUsd.setScale(2, RoundingMode.HALF_UP));
            resp.setOnsChangePercent(changePercent);
            resp.setOnsChange(onsUsd.subtract(prevClose).setScale(2, RoundingMode.HALF_UP));
            resp.setOnsHigh(safe(meta.getRegularMarketDayHigh()).setScale(2, RoundingMode.HALF_UP));
            resp.setOnsLow(safe(meta.getRegularMarketDayLow()).setScale(2, RoundingMode.HALF_UP));
            resp.setOnsPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));

            // Geriye dönük uyumluluk
            resp.setPrice(onsUsd.setScale(2, RoundingMode.HALF_UP));
            resp.setChange(resp.getOnsChange());
            resp.setChangePercent(changePercent);
            resp.setHigh(resp.getOnsHigh());
            resp.setLow(resp.getOnsLow());
            resp.setPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));
            resp.setBid(onsUsd.subtract(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));
            resp.setAsk(onsUsd.add(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));

        } catch (Exception e) {
            log.error("Yahoo fallback also failed: {}", e.getMessage());
        }

        resp.setLastUpdated(LocalDateTime.now(ISTANBUL).toString());
        resp.setUpdatedAt(resp.getLastUpdated());
        return resp;
    }

    /** BIST USD/Ons son noktasından ons verilerini spot response'a ekler. */
    private void enrichWithBistOns(GoldSpotResponse resp, BistPreciousMetalPoint ons) {
        BigDecimal close    = ons.getCloseUsdOns();
        BigDecimal high     = ons.getHighUsdOns();
        BigDecimal low      = ons.getLowUsdOns();
        BigDecimal wa       = ons.getWeightedAverageUsdOns();

        resp.setOnsUsd(close);
        resp.setOnsHigh(high);
        resp.setOnsLow(low);

        // Önceki kapanış için Yahoo'ya fallback (BIST önceki gün verisi ayrı sorgu gerektirir)
        // Şimdilik ons change hesabı Yahoo'dan alınır
        try {
            YahooChartSnapshot chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
            YahooStockMeta meta = chart.getMeta();
            BigDecimal prevClose = safe(meta.getPreviousClose());
            BigDecimal changePercent = BigDecimal.ZERO;
            if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                changePercent = close.subtract(prevClose)
                        .divide(prevClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            resp.setOnsChangePercent(changePercent);
            resp.setOnsChange(close.subtract(prevClose).setScale(2, RoundingMode.HALF_UP));
            resp.setOnsPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));
        } catch (Exception e) {
            log.warn("Could not get prevClose from Yahoo for ons change: {}", e.getMessage());
            resp.setOnsChangePercent(BigDecimal.ZERO);
            resp.setOnsChange(BigDecimal.ZERO);
        }

        // Geriye dönük uyumluluk
        resp.setPrice(close);
        resp.setChange(resp.getOnsChange());
        resp.setChangePercent(resp.getOnsChangePercent());
        resp.setHigh(high);
        resp.setLow(low);
        resp.setPreviousClose(resp.getOnsPreviousClose());
        resp.setBid(close != null ? close.subtract(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP) : null);
        resp.setAsk(close != null ? close.add(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP) : null);
    }

    /** ONS/USD verisini Yahoo'dan alıp spot response'a ekler (fallback). */
    private void enrichWithYahooOns(GoldSpotResponse resp) {
        try {
            YahooChartSnapshot chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
            YahooStockMeta meta = chart.getMeta();

            BigDecimal onsUsd    = safe(meta.getRegularMarketPrice());
            BigDecimal prevClose = safe(meta.getPreviousClose());
            BigDecimal high      = safe(meta.getRegularMarketDayHigh());
            BigDecimal low       = safe(meta.getRegularMarketDayLow());

            BigDecimal changePercent = BigDecimal.ZERO;
            if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                changePercent = onsUsd.subtract(prevClose)
                        .divide(prevClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            resp.setOnsUsd(onsUsd.setScale(2, RoundingMode.HALF_UP));
            resp.setOnsChangePercent(changePercent);
            resp.setOnsChange(onsUsd.subtract(prevClose).setScale(2, RoundingMode.HALF_UP));
            resp.setOnsHigh(high.setScale(2, RoundingMode.HALF_UP));
            resp.setOnsLow(low.setScale(2, RoundingMode.HALF_UP));
            resp.setOnsPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));

            // Geriye dönük uyumluluk
            resp.setPrice(onsUsd.setScale(2, RoundingMode.HALF_UP));
            resp.setChange(resp.getOnsChange());
            resp.setChangePercent(changePercent);
            resp.setHigh(high.setScale(2, RoundingMode.HALF_UP));
            resp.setLow(low.setScale(2, RoundingMode.HALF_UP));
            resp.setPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));
            resp.setBid(onsUsd.subtract(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));
            resp.setAsk(onsUsd.add(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));

        } catch (Exception e) {
            log.warn("Could not enrich spot with Yahoo ONS data: {}", e.getMessage());
        }
    }

    /** Portföy / modal: ons ve legacy alanları TCMB kuru ile TL'ye çevirir. */
    private void finalizeGoldSpotForTry(GoldSpotResponse resp) {
        if (resp == null) {
            return;
        }
        BigDecimal usdTry = resolveTcmbUsdTry();
        if (usdTry == null) {
            return;
        }
        resp.setUsdTry(usdTry);
        resp.setCurrency("TRY");
        if (resp.getOnsUsd() != null) {
            BigDecimal onsTry = resp.getOnsUsd().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
            resp.setOnsTry(onsTry);
            resp.setPriceTl(onsTry);
        }
    }

    private BigDecimal resolveTcmbUsdTry() {
        try {
            FxLatestRates fx = marketFxService.getTcmbLatestRates("USD");
            if (fx.getRates() == null) {
                return null;
            }
            return fx.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItem::getSell)
                    .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("TCMB USD/TRY unavailable for gold ons conversion: {}", e.getMessage());
            return null;
        }
    }

    // ── Range helpers ─────────────────────────────────────────────────────────

    /**
     * Range string'ini BIST tarih aralığına çevirir.
     * @return [startDate, endDate] yyyy-MM-dd
     */
    private String[] rangeToBistDates(String range) {
        LocalDate today = LocalDate.now(ISTANBUL);
        LocalDate start = switch (range == null ? "1M" : range.toUpperCase()) {
            case "1D"  -> today.minusDays(5);   // son 5 gün (hafta sonu güvencesi)
            case "1W"  -> today.minusDays(10);
            case "3M"  -> today.minusMonths(3);
            case "1Y"  -> today.minusYears(1);
            case "5Y"  -> today.minusYears(5);
            case "ALL" -> LocalDate.of(2011, 1, 1); // BIST başlangıç tarihi
            default    -> today.minusMonths(1); // 1M
        };
        return new String[]{start.format(DATE_FMT), today.format(DATE_FMT)};
    }

    private String[] rangeToYahooParams(String range) {
        return switch (range == null ? "1M" : range.toUpperCase()) {
            case "1D"  -> new String[]{"1d",  "5m"};
            case "1W"  -> new String[]{"5d",  "1h"};
            case "3M"  -> new String[]{"3mo", "1d"};
            case "1Y"  -> new String[]{"1y",  "1d"};
            case "ALL" -> new String[]{"5y",  "1wk"};
            default    -> new String[]{"1mo", "1d"};
        };
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
