package com.finance.portal.market.application.gold;

import com.finance.portal.market.application.stock.port.YahooStockPort;
import com.finance.portal.market.infrastructure.external.fx.TcmbFxClient;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class GoldMarketService {

    private static final Logger log = LoggerFactory.getLogger(GoldMarketService.class);

    // 1 troy ons = 31.1035 gram
    private static final BigDecimal TROY_OZ_TO_GRAM = new BigDecimal("31.1035");
    // Çeyrek altın ≈ 1.75 gram (22 ayar, yaklaşık)
    private static final BigDecimal CEYREK_GRAM = new BigDecimal("1.7517");
    // Yarım altın ≈ 3.5 gram
    private static final BigDecimal YARIM_GRAM = new BigDecimal("3.5033");
    // Tam altın ≈ 7.0 gram
    private static final BigDecimal TAM_GRAM = new BigDecimal("7.0066");

    private static final String GOLD_SYMBOL = "GC=F";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final YahooStockPort yahooStockPort;
    private final TcmbFxClient tcmbFxClient;

    public GoldMarketService(YahooStockPort yahooStockPort, TcmbFxClient tcmbFxClient) {
        this.yahooStockPort = yahooStockPort;
        this.tcmbFxClient = tcmbFxClient;
    }

    // ── Spot Data ─────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.gold.spot", key = "'spot'")
    public GoldSpotResponse getSpotGold() {
        try {
            // 1. Yahoo'dan GC=F güncel veri
            YahooChartResponseDto chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m");
            YahooChartResponseDto.Meta meta = chart.getChart().getResult().get(0).getMeta();

            // 2. TCMB'den USD/TRY kuru
            BigDecimal usdTry = getUsdTryRate();

            BigDecimal priceUsd = safeDecimal(meta.getRegularMarketPrice());
            BigDecimal prevClose = safeDecimal(meta.getPreviousClose());
            if (prevClose.compareTo(BigDecimal.ZERO) == 0) {
                prevClose = safeDecimal(meta.getRegularMarketDayLow()); // fallback
            }
            BigDecimal change = priceUsd.subtract(prevClose).setScale(2, RoundingMode.HALF_UP);
            BigDecimal changePct = prevClose.compareTo(BigDecimal.ZERO) != 0
                    ? change.divide(prevClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // TRY hesaplamaları
            BigDecimal priceTl = priceUsd.multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gramTl = priceUsd.divide(TROY_OZ_TO_GRAM, 8, RoundingMode.HALF_UP)
                    .multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ceyrekTl = gramTl.multiply(CEYREK_GRAM).setScale(2, RoundingMode.HALF_UP);
            BigDecimal yarimTl = gramTl.multiply(YARIM_GRAM).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tamTl = gramTl.multiply(TAM_GRAM).setScale(2, RoundingMode.HALF_UP);

            GoldSpotResponse resp = new GoldSpotResponse();
            resp.setSymbol(GOLD_SYMBOL);
            resp.setName("Altın/Ons");
            resp.setCurrency("USD");
            resp.setPrice(priceUsd.setScale(2, RoundingMode.HALF_UP));
            resp.setChange(change);
            resp.setChangePercent(changePct);
            resp.setHigh(safeDecimal(meta.getRegularMarketDayHigh()).setScale(2, RoundingMode.HALF_UP));
            resp.setLow(safeDecimal(meta.getRegularMarketDayLow()).setScale(2, RoundingMode.HALF_UP));
            resp.setPreviousClose(prevClose.setScale(2, RoundingMode.HALF_UP));
            resp.setBid(priceUsd.subtract(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));
            resp.setAsk(priceUsd.add(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP));
            resp.setPriceTl(priceTl);
            resp.setGramTl(gramTl);
            resp.setCeyrekTl(ceyrekTl);
            resp.setYarimTl(yarimTl);
            resp.setTamTl(tamTl);
            resp.setUsdTry(usdTry);

            if (meta.getRegularMarketTime() != null) {
                resp.setUpdatedAt(Instant.ofEpochSecond(meta.getRegularMarketTime())
                        .atZone(ISTANBUL).toString());
            }

            return resp;
        } catch (Exception e) {
            log.error("Failed to fetch gold spot data: {}", e.getMessage());
            throw new RuntimeException("Altın verisi alınamadı: " + e.getMessage(), e);
        }
    }

    // ── History Data ──────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.gold.history", key = "#range + ':' + #currency")
    public GoldHistoryResponse getGoldHistory(String range, String currency) {
        try {
            String[] params = rangeToYahooParams(range);
            String yahooRange = params[0];
            String interval = params[1];

            YahooChartResponseDto chart = yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, yahooRange, interval);
            YahooChartResponseDto.Result result = chart.getChart().getResult().get(0);

            List<Long> timestamps = result.getTimestamp();
            List<BigDecimal> closes = null;
            List<BigDecimal> opens = null;
            List<BigDecimal> highs = null;
            List<BigDecimal> lows = null;
            List<Long> volumes = null;

            if (result.getIndicators() != null && result.getIndicators().getQuote() != null
                    && !result.getIndicators().getQuote().isEmpty()) {
                YahooChartResponseDto.Quote q = result.getIndicators().getQuote().get(0);
                closes = q.getClose();
                opens = q.getOpen();
                highs = q.getHigh();
                lows = q.getLow();
                volumes = q.getVolume();
            }

            // TRY için USD/TRY kuru
            BigDecimal usdTry = "TRY".equalsIgnoreCase(currency) ? getUsdTryRate() : null;

            List<GoldHistoryPoint> points = new ArrayList<>();
            int size = timestamps != null ? timestamps.size() : 0;

            for (int i = 0; i < size; i++) {
                Long ts = timestamps.get(i);
                BigDecimal close = closes != null && i < closes.size() ? closes.get(i) : null;
                if (ts == null || close == null) continue;

                String date = Instant.ofEpochSecond(ts).atZone(ISTANBUL)
                        .toLocalDate().format(DATE_FMT);

                BigDecimal finalClose = close;
                if (usdTry != null) {
                    // TRY: gram altın = (ons / 31.1035) * usdTry
                    finalClose = close.divide(TROY_OZ_TO_GRAM, 8, RoundingMode.HALF_UP)
                            .multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }

                GoldHistoryPoint pt = new GoldHistoryPoint();
                pt.setDate(date);
                pt.setClose(finalClose.setScale(2, RoundingMode.HALF_UP));
                if (opens != null && i < opens.size() && opens.get(i) != null)
                    pt.setOpen(opens.get(i).setScale(2, RoundingMode.HALF_UP));
                if (highs != null && i < highs.size() && highs.get(i) != null)
                    pt.setHigh(highs.get(i).setScale(2, RoundingMode.HALF_UP));
                if (lows != null && i < lows.size() && lows.get(i) != null)
                    pt.setLow(lows.get(i).setScale(2, RoundingMode.HALF_UP));
                if (volumes != null && i < volumes.size())
                    pt.setVolume(volumes.get(i));

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
            throw new RuntimeException("Altın tarihsel verisi alınamadı: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * range → [yahooRange, interval]
     * 1D  → 1d, 5m
     * 1W  → 5d, 1h
     * 1M  → 1mo, 1d
     * 3M  → 3mo, 1d
     * 1Y  → 1y, 1d
     * ALL → 5y, 1wk
     */
    private String[] rangeToYahooParams(String range) {
        return switch (range == null ? "1M" : range.toUpperCase()) {
            case "1D" -> new String[]{"1d", "5m"};
            case "1W" -> new String[]{"5d", "1h"};
            case "3M" -> new String[]{"3mo", "1d"};
            case "1Y" -> new String[]{"1y", "1d"};
            case "ALL" -> new String[]{"5y", "1wk"};
            default -> new String[]{"1mo", "1d"}; // 1M
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
        return new BigDecimal("44.50"); // fallback
    }

    private BigDecimal safeDecimal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
