package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * GOLD watchlist item zenginleştirmesi (GOLD/GRAM/CEYREK/YARIM/TAM/CUMHUR/14AYAR/22AYAR).
 * BIST has altın gram referansından teorik türev fiyatlar; ons ise USD × USD/TRY = TL.
 * Davranış {@code PortfolioWatchlistMarketEnricher.enrichGold + latestFromGoldHistory + goldCloses1y +
 * watchlistGoldFactor} eski kodundan aynen taşındı.
 */
@Component
public class GoldWatchlistEnricher {

    private static final BigDecimal K22 = new BigDecimal("0.9166");

    private final GoldMarketService goldMarketService;

    public GoldWatchlistEnricher(GoldMarketService goldMarketService) {
        this.goldMarketService = goldMarketService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        GoldSpotResponse spot = goldMarketService.getSpotGold();
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(spot.getLastUpdated());
        if (asOf == null) {
            asOf = PortfolioDateTimeParse.parseLenient(spot.getUpdatedAt());
        }
        r.setAsOf(asOf);

        String upper = symbol.toUpperCase(Locale.ROOT);
        applySpotFields(r, spot, upper);
        applyWeeklyHistoryOverlay(r, spot, upper);
        applyMaAnd52w(r, closes1y(upper));
    }

    private static void applySpotFields(WatchlistItemResponse r, GoldSpotResponse spot, String upper) {
        switch (upper) {
            case "GOLD" -> applyOnsSpot(r, spot);
            case "GRAM" -> {
                r.setLastPrice(spot.getGramGoldTry());
                r.setCurrency("TRY");
                r.setHigh(spot.getGramHighTry());
                r.setLow(spot.getGramLowTry());
            }
            case "CEYREK" -> { r.setLastPrice(spot.getQuarterGoldTry()); r.setCurrency("TRY"); }
            case "YARIM"  -> { r.setLastPrice(spot.getHalfGoldTry());    r.setCurrency("TRY"); }
            case "TAM", "ZIYNET" -> { r.setLastPrice(spot.getZiynetGoldTry()); r.setCurrency("TRY"); }
            case "CUMHUR", "ATA" -> { r.setLastPrice(spot.getRepublicGoldTry()); r.setCurrency("TRY"); }
            case "14AYAR", "AYAR14" -> {
                r.setLastPrice(spot.getFourteenKBraceletTry() != null
                        ? spot.getFourteenKBraceletTry() : spot.getAyar14Tl());
                r.setCurrency("TRY");
            }
            case "22AYAR", "AYAR22" -> {
                r.setLastPrice(spot.getTwentyTwoKBraceletTry() != null
                        ? spot.getTwentyTwoKBraceletTry() : spot.getAyar22Tl());
                r.setCurrency("TRY");
            }
            default -> throw new UnsupportedOperationException("Unsupported gold symbol: " + upper);
        }
    }

    private static void applyOnsSpot(WatchlistItemResponse r, GoldSpotResponse spot) {
        BigDecimal onsTry = spot.getOnsTry();
        if (onsTry == null && spot.getOnsUsd() != null && spot.getUsdTry() != null) {
            onsTry = spot.getOnsUsd().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP);
        }
        r.setLastPrice(onsTry);
        r.setCurrency("TRY");
        BigDecimal usdTry = spot.getUsdTry();
        if (spot.getOnsHigh() != null && usdTry != null) {
            r.setHigh(spot.getOnsHigh().multiply(usdTry).setScale(2, RoundingMode.HALF_UP));
        } else {
            r.setHigh(spot.getOnsHigh());
        }
        if (spot.getOnsLow() != null && usdTry != null) {
            r.setLow(spot.getOnsLow().multiply(usdTry).setScale(2, RoundingMode.HALF_UP));
        } else {
            r.setLow(spot.getOnsLow());
        }
        if (spot.getOnsChange() != null && usdTry != null) {
            r.setChange(spot.getOnsChange().multiply(usdTry).setScale(2, RoundingMode.HALF_UP));
        } else {
            r.setChange(spot.getOnsChange());
        }
        r.setChangePercent(spot.getOnsChangePercent());
    }

    /** 1W history son satırından open/high/low/volume tamamla; sikke/ayar için gram türevi orana göre ölçeklenir. */
    private void applyWeeklyHistoryOverlay(WatchlistItemResponse r, GoldSpotResponse spot, String upper) {
        try {
            if ("GOLD".equals(upper)) {
                GoldHistoryPoint lp = latestFromHistory(goldMarketService.getGoldHistory("1W", "USD"));
                if (lp != null) {
                    r.setOpen(lp.getOpen());
                    r.setHigh(lp.getHigh() != null ? lp.getHigh() : r.getHigh());
                    r.setLow(lp.getLow() != null ? lp.getLow() : r.getLow());
                    r.setVolume(lp.getVolume());
                }
                return;
            }
            GoldHistoryPoint lp = latestFromHistory(goldMarketService.getGoldHistory("1W", "TRY"));
            if (lp == null) {
                return;
            }
            if ("GRAM".equals(upper)) {
                r.setOpen(lp.getOpen());
                r.setVolume(lp.getVolume());
                if (lp.getClose() != null) {
                    r.setLastPrice(lp.getClose());
                }
                r.setHigh(lp.getHigh());
                r.setLow(lp.getLow());
                if (lp.getClose() != null && lp.getOpen() != null) {
                    BigDecimal ch = lp.getClose().subtract(lp.getOpen());
                    r.setChange(ch);
                    if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                        r.setChangePercent(ch.divide(lp.getOpen(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                }
                return;
            }
            // Sikke/ayar: gram referansından oran ile ölçekleme
            BigDecimal gramRef = spot.getOfficialPureGoldGramTry() != null
                    ? spot.getOfficialPureGoldGramTry()
                    : spot.getGramGoldTry();
            if (gramRef != null && gramRef.compareTo(BigDecimal.ZERO) != 0
                    && lp.getClose() != null && lp.getOpen() != null
                    && r.getLastPrice() != null) {
                BigDecimal gramCh = lp.getClose().subtract(lp.getOpen());
                BigDecimal ratio = r.getLastPrice().divide(gramRef, 8, RoundingMode.HALF_UP);
                BigDecimal coinChange = gramCh.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                r.setChange(coinChange);
                if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal gramPct = gramCh.divide(lp.getOpen(), 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    r.setChangePercent(gramPct);
                }
                r.setOpen(r.getLastPrice().subtract(coinChange));
                if (lp.getHigh() != null) {
                    r.setHigh(lp.getHigh().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                }
                if (lp.getLow() != null) {
                    r.setLow(lp.getLow().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                }
                r.setVolume(lp.getVolume());
            } else {
                r.setVolume(lp.getVolume());
            }
        } catch (Exception ignored) {
            // 1W overlay başarısız olursa spot zaten doldurdu, sessizce geç.
        }
    }

    private static GoldHistoryPoint latestFromHistory(GoldHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return null;
        }
        List<GoldHistoryPoint> pts = resp.getPoints();
        return pts.get(pts.size() - 1);
    }

    /** 1Y gold series — GOLD ons için TRY çevirisi; gram/sikke için teorik çarpan. */
    private List<BigDecimal> closes1y(String upper) {
        try {
            if ("GOLD".equals(upper)) {
                BigDecimal usdTry = goldMarketService.getSpotGold().getUsdTry();
                if (usdTry == null) {
                    return null;
                }
                GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "USD");
                if (hist == null || hist.getPoints() == null) {
                    return null;
                }
                return hist.getPoints().stream()
                        .map(GoldHistoryPoint::getClose)
                        .filter(Objects::nonNull)
                        .map(c -> c.multiply(usdTry).setScale(2, RoundingMode.HALF_UP))
                        .collect(Collectors.toList());
            }
            GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "TRY");
            if (hist == null || hist.getPoints() == null) {
                return null;
            }
            BigDecimal factor = theoryFactor(upper);
            return hist.getPoints().stream()
                    .map(GoldHistoryPoint::getClose)
                    .filter(Objects::nonNull)
                    .map(g -> factor == null ? g : g.multiply(factor).setScale(2, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }

    /** Gram altın serisini sikke/ayar fiyatına çeviren teorik çarpan (holdings ile aynı). */
    private static BigDecimal theoryFactor(String upper) {
        return switch (upper) {
            case "GRAM" -> null;
            case "14AYAR", "AYAR14" -> new BigDecimal("0.5850");
            case "22AYAR", "AYAR22" -> K22;
            case "CEYREK" -> new BigDecimal("1.754").multiply(K22);
            case "YARIM" -> new BigDecimal("3.508").multiply(K22);
            case "TAM", "ZIYNET" -> new BigDecimal("7.016").multiply(K22);
            case "CUMHUR", "ATA" -> new BigDecimal("7.216").multiply(K22);
            default -> null;
        };
    }
}
