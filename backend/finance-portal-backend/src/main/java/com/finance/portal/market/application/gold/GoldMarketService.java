package com.finance.portal.market.application.gold;

import com.finance.portal.market.application.stock.port.YahooStockPort;
import com.finance.portal.market.infrastructure.external.fx.TcmbFxClient;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.gold.GoldPriceEntry;
import com.finance.portal.market.infrastructure.external.gold.GoldScraper;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GoldMarketService {

    private static final Logger log = LoggerFactory.getLogger(GoldMarketService.class);

    // 1 troy ons = 31.1035 gram
    private static final BigDecimal TROY_OZ_TO_GRAM = new BigDecimal("31.1035");

    private static final String GOLD_SYMBOL = "GC=F";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final YahooStockPort yahooStockPort;
    private final TcmbFxClient tcmbFxClient;
    private final GoldScraper goldScraper;

    // Saatte 1 guncellenen scrape cache
    private volatile Map<String, GoldPriceEntry> scrapeCache = null;
    private volatile String scrapedAt = null;

    public GoldMarketService(YahooStockPort yahooStockPort,
                             TcmbFxClient tcmbFxClient,
                             GoldScraper goldScraper) {
        this.yahooStockPort = yahooStockPort;
        this.tcmbFxClient = tcmbFxClient;
        this.goldScraper = goldScraper;
    }

    // ── Saatte 1 scrape ───────────────────────────────────────────────────────

    /**
     * Saatte bir canlialtinfiyatlari.com'dan fiyatlari ceker ve cache'i temizler.
     * Uygulama baslarken de calisir (initialDelay=0).
     */
    @Scheduled(initialDelay = 0, fixedRate = 3_600_000) // her saat
    @CacheEvict(cacheNames = "market.gold.spot", allEntries = true)
    public void refreshScrapeCache() {
        log.info("Refreshing gold scrape cache from canlialtinfiyatlari.com...");
        Map<String, GoldPriceEntry> fresh = goldScraper.fetchAll();
        if (!fresh.isEmpty()) {
            scrapeCache = fresh;
            scrapedAt = LocalDateTime.now(ISTANBUL).toString();
            log.info("Gold scrape cache updated: {} entries at {}", fresh.size(), scrapedAt);
        } else {
            log.warn("Gold scrape returned empty, keeping previous cache");
        }
    }

    // ── Spot Data ─────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.gold.spot", key = "'spot'")
    public GoldSpotResponse getSpotGold() {
        // Scrape cache yoksa hemen cek
        if (scrapeCache == null || scrapeCache.isEmpty()) {
            log.info("Scrape cache empty, fetching now...");
            scrapeCache = goldScraper.fetchAll();
            scrapedAt = LocalDateTime.now(ISTANBUL).toString();
        }

        Map<String, GoldPriceEntry> prices = scrapeCache;

        // Temel fiyatlar scrape'den
        GoldPriceEntry onsEntry   = prices.get("ALTIN_ONS");
        GoldPriceEntry gramEntry  = prices.get("GRAM_ALTIN");
        GoldPriceEntry hasEntry   = prices.get("HAS_ALTIN");
        GoldPriceEntry ceyrekEntry = prices.get("CEYREK_ALTIN");
        GoldPriceEntry yarimEntry  = prices.get("YARIM_ALTIN");
        GoldPriceEntry tamEntry    = prices.get("TAM_ALTIN");
        GoldPriceEntry ataEntry    = prices.get("ATA_ALTIN");
        GoldPriceEntry ayar14Entry = prices.get("AYAR_14");
        GoldPriceEntry ayar22Entry = prices.get("AYAR_22");

        // USD/TRY kuru (TCMB)
        BigDecimal usdTry = getUsdTryRate();

        // ONS fiyati USD — scrape'den alış/satış ortalamasi
        BigDecimal onsUsd = BigDecimal.ZERO;
        BigDecimal onsBid = BigDecimal.ZERO;
        BigDecimal onsAsk = BigDecimal.ZERO;
        BigDecimal changePercent = BigDecimal.ZERO;

        if (onsEntry != null) {
            onsBid = onsEntry.getBuy();
            onsAsk = onsEntry.getSell();
            onsUsd = onsEntry.getMid();
            if (onsEntry.getChangePercent() != null) changePercent = onsEntry.getChangePercent();
        } else {
            // Fallback: Yahoo'dan cek
            try {
                YahooChartResponseDto chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
                YahooChartResponseDto.Meta meta = chart.getChart().getResult().get(0).getMeta();
                onsUsd = safeDecimal(meta.getRegularMarketPrice());
                onsBid = onsUsd.subtract(new BigDecimal("0.50"));
                onsAsk = onsUsd.add(new BigDecimal("0.50"));
                BigDecimal prevClose = safeDecimal(meta.getPreviousClose());
                if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    changePercent = onsUsd.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception e) {
                log.error("Yahoo fallback also failed: {}", e.getMessage());
            }
        }

        // Gram altin TRY — scrape'den, yoksa ONS'tan hesapla
        BigDecimal gramTl;
        if (gramEntry != null) {
            gramTl = gramEntry.getMid();
        } else if (hasEntry != null) {
            gramTl = hasEntry.getMid();
        } else {
            gramTl = onsUsd.divide(TROY_OZ_TO_GRAM, 8, RoundingMode.HALF_UP)
                    .multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
        }

        // Ceyrek, Yarim, Tam — scrape'den, yoksa gramTl'den hesapla
        BigDecimal ceyrekTl = ceyrekEntry != null ? ceyrekEntry.getMid()
                : gramTl.multiply(new BigDecimal("1.75"))
                        .multiply(new BigDecimal("22").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal yarimTl = yarimEntry != null ? yarimEntry.getMid()
                : gramTl.multiply(new BigDecimal("3.50"))
                        .multiply(new BigDecimal("22").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tamTl = tamEntry != null ? tamEntry.getMid()
                : gramTl.multiply(new BigDecimal("7.00"))
                        .multiply(new BigDecimal("22").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        // Cumhuriyet (Ata) altini — scrape'den
        BigDecimal cumhuriyetTl = ataEntry != null ? ataEntry.getMid()
                : gramTl.multiply(new BigDecimal("7.216"))
                        .multiply(new BigDecimal("22").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        // 14/22 Ayar bilezik — scrape'den, yoksa gramTl'den
        BigDecimal ayar14Tl = ayar14Entry != null ? ayar14Entry.getMid()
                : gramTl.multiply(new BigDecimal("14").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal ayar22Tl = ayar22Entry != null ? ayar22Entry.getMid()
                : gramTl.multiply(new BigDecimal("22").divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);

        // ONS TL karsiligi
        BigDecimal priceTl = onsUsd.multiply(usdTry).setScale(2, RoundingMode.HALF_UP);

        // Degisim miktari (USD)
        BigDecimal change = onsUsd.multiply(changePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // High/Low/PreviousClose — Yahoo'dan almaya devam et (history icin)
        BigDecimal high = onsUsd;
        BigDecimal low = onsUsd;
        BigDecimal previousClose = onsUsd;
        try {
            YahooChartResponseDto chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
            YahooChartResponseDto.Meta meta = chart.getChart().getResult().get(0).getMeta();
            high = safeDecimal(meta.getRegularMarketDayHigh());
            low  = safeDecimal(meta.getRegularMarketDayLow());
            previousClose = safeDecimal(meta.getPreviousClose());
            if (previousClose.compareTo(BigDecimal.ZERO) == 0) previousClose = onsUsd;
        } catch (Exception e) {
            log.warn("Could not fetch Yahoo high/low/prevClose: {}", e.getMessage());
        }

        GoldSpotResponse resp = new GoldSpotResponse();
        resp.setSymbol(GOLD_SYMBOL);
        resp.setName("Altin/Ons");
        resp.setCurrency("USD");
        resp.setPrice(onsUsd.setScale(2, RoundingMode.HALF_UP));
        resp.setChange(change);
        resp.setChangePercent(changePercent);
        resp.setHigh(high.setScale(2, RoundingMode.HALF_UP));
        resp.setLow(low.setScale(2, RoundingMode.HALF_UP));
        resp.setPreviousClose(previousClose.setScale(2, RoundingMode.HALF_UP));
        resp.setBid(onsBid.setScale(2, RoundingMode.HALF_UP));
        resp.setAsk(onsAsk.setScale(2, RoundingMode.HALF_UP));
        resp.setPriceTl(priceTl);
        resp.setGramTl(gramTl.setScale(2, RoundingMode.HALF_UP));
        resp.setCeyrekTl(ceyrekTl);
        resp.setYarimTl(yarimTl);
        resp.setTamTl(tamTl);
        resp.setCumhuriyetTl(cumhuriyetTl);
        resp.setAyar14Tl(ayar14Tl);
        resp.setAyar22Tl(ayar22Tl);
        resp.setUsdTry(usdTry);

        // Gerçek alış/satış — scrape'den, yoksa mid ± %0.1 fallback
        resp.setGramBuy(gramEntry  != null ? gramEntry.getBuy()   : gramTl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setGramSell(gramEntry != null ? gramEntry.getSell()  : gramTl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setCeyrekBuy(ceyrekEntry  != null ? ceyrekEntry.getBuy()  : ceyrekTl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setCeyrekSell(ceyrekEntry != null ? ceyrekEntry.getSell() : ceyrekTl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setYarimBuy(yarimEntry  != null ? yarimEntry.getBuy()  : yarimTl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setYarimSell(yarimEntry != null ? yarimEntry.getSell() : yarimTl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setTamBuy(tamEntry  != null ? tamEntry.getBuy()  : tamTl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setTamSell(tamEntry != null ? tamEntry.getSell() : tamTl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setCumhuriyetBuy(ataEntry  != null ? ataEntry.getBuy()  : cumhuriyetTl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setCumhuriyetSell(ataEntry != null ? ataEntry.getSell() : cumhuriyetTl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setAyar14Buy(ayar14Entry  != null ? ayar14Entry.getBuy()  : ayar14Tl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setAyar14Sell(ayar14Entry != null ? ayar14Entry.getSell() : ayar14Tl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setAyar22Buy(ayar22Entry  != null ? ayar22Entry.getBuy()  : ayar22Tl.multiply(new BigDecimal("0.999")).setScale(2, RoundingMode.HALF_UP));
        resp.setAyar22Sell(ayar22Entry != null ? ayar22Entry.getSell() : ayar22Tl.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP));
        resp.setUpdatedAt(scrapedAt != null ? scrapedAt : LocalDateTime.now(ISTANBUL).toString());

        return resp;
    }

    // ── History Data — Yahoo Finance (degismez) ───────────────────────────────

    @Cacheable(cacheNames = "market.gold.history", key = "#range + ':' + #currency")
    public GoldHistoryResponse getGoldHistory(String range, String currency) {
        try {
            String[] params = rangeToYahooParams(range);
            String yahooRange = params[0];
            String interval = params[1];

            boolean isTry = "TRY".equalsIgnoreCase(currency);

            YahooChartResponseDto goldChart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, yahooRange, interval);
            YahooChartResponseDto.Result goldResult = goldChart.getChart().getResult().get(0);

            java.util.Map<String, BigDecimal> usdTryByDate = new java.util.HashMap<>();
            if (isTry) {
                try {
                    YahooChartResponseDto fxChart = yahooStockPort.fetchChartWithParams("USDTRY=X", yahooRange, interval);
                    YahooChartResponseDto.Result fxResult = fxChart.getChart().getResult().get(0);
                    List<Long> fxTs = fxResult.getTimestamp();
                    List<BigDecimal> fxCloses = null;
                    if (fxResult.getIndicators() != null && fxResult.getIndicators().getQuote() != null
                            && !fxResult.getIndicators().getQuote().isEmpty()) {
                        fxCloses = fxResult.getIndicators().getQuote().get(0).getClose();
                    }
                    if (fxTs != null && fxCloses != null) {
                        for (int i = 0; i < Math.min(fxTs.size(), fxCloses.size()); i++) {
                            if (fxTs.get(i) != null && fxCloses.get(i) != null) {
                                String d = Instant.ofEpochSecond(fxTs.get(i)).atZone(ISTANBUL).toLocalDate().format(DATE_FMT);
                                usdTryByDate.put(d, fxCloses.get(i));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch USDTRY history: {}", e.getMessage());
                }
                if (usdTryByDate.isEmpty()) {
                    usdTryByDate.put("__default__", getUsdTryRate());
                }
            }

            List<Long> timestamps = goldResult.getTimestamp();
            List<BigDecimal> closes = null, opens = null, highs = null, lows = null;
            List<Long> volumes = null;

            if (goldResult.getIndicators() != null && goldResult.getIndicators().getQuote() != null
                    && !goldResult.getIndicators().getQuote().isEmpty()) {
                YahooChartResponseDto.Quote q = goldResult.getIndicators().getQuote().get(0);
                closes = q.getClose();
                opens  = q.getOpen();
                highs  = q.getHigh();
                lows   = q.getLow();
                volumes = q.getVolume();
            }

            BigDecimal fallbackRate = usdTryByDate.getOrDefault("__default__", getUsdTryRate());

            List<GoldHistoryPoint> points = new ArrayList<>();
            int size = timestamps != null ? timestamps.size() : 0;

            for (int i = 0; i < size; i++) {
                Long ts = timestamps.get(i);
                BigDecimal close = closes != null && i < closes.size() ? closes.get(i) : null;
                if (ts == null || close == null) continue;

                String date = Instant.ofEpochSecond(ts).atZone(ISTANBUL).toLocalDate().format(DATE_FMT);

                BigDecimal finalClose = close;
                if (isTry) {
                    BigDecimal dayRate = usdTryByDate.getOrDefault(date, fallbackRate);
                    finalClose = close.divide(TROY_OZ_TO_GRAM, 8, RoundingMode.HALF_UP)
                            .multiply(dayRate).setScale(2, RoundingMode.HALF_UP);
                }

                GoldHistoryPoint pt = new GoldHistoryPoint();
                pt.setDate(date);
                pt.setClose(finalClose.setScale(2, RoundingMode.HALF_UP));
                if (opens  != null && i < opens.size()  && opens.get(i)  != null) pt.setOpen(opens.get(i).setScale(2, RoundingMode.HALF_UP));
                if (highs  != null && i < highs.size()  && highs.get(i)  != null) pt.setHigh(highs.get(i).setScale(2, RoundingMode.HALF_UP));
                if (lows   != null && i < lows.size()   && lows.get(i)   != null) pt.setLow(lows.get(i).setScale(2, RoundingMode.HALF_UP));
                if (volumes != null && i < volumes.size()) pt.setVolume(volumes.get(i));

                points.add(pt);
            }

            GoldHistoryResponse resp = new GoldHistoryResponse();
            resp.setSymbol(GOLD_SYMBOL);
            resp.setRange(range);
            resp.setCurrency(currency != null ? currency.toUpperCase() : "USD");
            resp.setPoints(points);
            return resp;

        } catch (Exception e) {
            log.error("Failed to fetch gold history for range={}: {}", range, e.getMessage());
            throw new RuntimeException("Altin tarihsel verisi alinamadi: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private BigDecimal getUsdTryRate() {
        try {
            TcmbCurrencyDto usd = tcmbFxClient.fetchLatestRates().getCurrencies().stream()
                    .filter(c -> "USD".equals(c.getCurrencyCode()))
                    .findFirst().orElse(null);
            if (usd != null && usd.getForexSelling() != null) {
                return new BigDecimal(usd.getForexSelling());
            }
        } catch (Exception e) {
            log.warn("Failed to get USD/TRY rate: {}", e.getMessage());
        }
        return new BigDecimal("44.50");
    }

    private BigDecimal safeDecimal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
