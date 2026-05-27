package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.applyMasFromCloses;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * GOLD holding zenginleştirmesi. Kaynak: {@link GoldMarketService} (BigPara spot + 1Y history).
 * Bilezik/Sikke/Gram/Ons gibi farklı kontratları aynı API üzerinden tek bir branch'te toplar;
 * 22K/14K saflık ve sikke gram-eşdeğer çarpanları teorik formülle hesaplanır.
 *
 * <p>Davranış {@code PortfolioHoldingMarketEnricher.enrichGoldHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class GoldHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(GoldHoldingEnricher.class);

    /** 22K saflık (= ham gram × 0.9166). */
    private static final BigDecimal FINENESS_22K = new BigDecimal("0.9166");
    /** 14K saflık. */
    private static final BigDecimal FINENESS_14K = new BigDecimal("0.5850");
    /** Çeyrek altın ham gramaj. */
    private static final BigDecimal GROSS_QUARTER  = new BigDecimal("1.754");
    /** Yarım altın ham gramaj. */
    private static final BigDecimal GROSS_HALF     = new BigDecimal("3.508");
    /** Ziynet (Tam) ham gramaj. */
    private static final BigDecimal GROSS_ZIYNET   = new BigDecimal("7.016");
    /** Cumhuriyet (Ata) ham gramaj. */
    private static final BigDecimal GROSS_REPUBLIC = new BigDecimal("7.216");

    private final GoldMarketService goldMarketService;
    private final MarketFxService marketFxService;

    public GoldHoldingEnricher(GoldMarketService goldMarketService, MarketFxService marketFxService) {
        this.goldMarketService = goldMarketService;
        this.marketFxService = marketFxService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        GoldSpotResponse spot = goldMarketService.getSpotGold();
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(spot.getLastUpdated());
        if (asOf == null) {
            asOf = PortfolioDateTimeParse.parseLenient(spot.getUpdatedAt());
        }

        String upper = holding.getSymbol().toUpperCase(Locale.ROOT);
        BigDecimal price;
        String currency;
        BigDecimal change = null;
        BigDecimal changePercent = null;
        BigDecimal high = null;
        BigDecimal low  = null;

        switch (upper) {
            case "GOLD" -> {
                // İşlem fiyatı TRY/ons → canlı fiyat da TRY/ons (ham USD/ons kullanılmaz)
                price = spot.getOnsTry();
                if (price == null && spot.getOnsUsd() != null && spot.getUsdTry() != null) {
                    price = spot.getOnsUsd().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP);
                }
                currency = "TRY";
                BigDecimal usdTry = spot.getUsdTry();
                if (spot.getOnsChange() != null && usdTry != null) {
                    change = spot.getOnsChange().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
                changePercent = spot.getOnsChangePercent();
                if (spot.getOnsHigh() != null && usdTry != null) {
                    high = spot.getOnsHigh().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
                if (spot.getOnsLow() != null && usdTry != null) {
                    low = spot.getOnsLow().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "GRAM" -> {
                price    = spot.getGramGoldTry() != null ? spot.getGramGoldTry() : spot.getGramTl();
                currency = "TRY";
                high     = spot.getGramHighTry();
                low      = spot.getGramLowTry();
                change   = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            case "CEYREK" -> { price = spot.getQuarterGoldTry();   currency = "TRY"; }
            case "YARIM"  -> { price = spot.getHalfGoldTry();      currency = "TRY"; }
            case "TAM"    -> { price = spot.getZiynetGoldTry();    currency = "TRY"; }
            case "ZIYNET" -> { price = spot.getZiynetGoldTry();    currency = "TRY"; }
            case "CUMHUR", "ATA" -> { price = spot.getRepublicGoldTry(); currency = "TRY"; }
            case "14AYAR", "AYAR14" -> {
                price = spot.getFourteenKBraceletTry() != null ? spot.getFourteenKBraceletTry() : spot.getAyar14Tl();
                currency = "TRY";
                change = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            case "22AYAR", "AYAR22" -> {
                price = spot.getTwentyTwoKBraceletTry() != null ? spot.getTwentyTwoKBraceletTry() : spot.getAyar22Tl();
                currency = "TRY";
                change = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            default -> throw new UnsupportedOperationException("Unsupported gold symbol: " + upper);
        }

        if (price == null) {
            // GOLD (ons) için bile ham spot yoksa: kullanıcıya anlamlı bir holding (sembol + asOf + hacim) bırak.
            if ("GOLD".equals(upper)) {
                holding.setCurrency("TRY");
                holding.setName("Altın (Ons)");
                holding.setAsOf(asOf);
                if (spot.getQuantityKg() != null) {
                    holding.setVolume(spot.getQuantityKg().longValue());
                }
                return;
            }
            throw new IllegalStateException("Gold price unavailable for: " + upper);
        }

        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

        // Sikke/ziynet (Çeyrek/Yarım/Tam/Ata) için günlük değişim spot'ta ayrı gelmiyor;
        // gram altınla aynı oranda hareket ettiklerinden gram günlük %'sinden türetilir
        // (değişim tutarı = fiyat × %/100). GRAM/ONS/14-22 ayar zaten kendi değerini taşır.
        if (changePercent == null && spot.getChangePercent() != null) {
            changePercent = spot.getChangePercent();
        }
        if (change == null && changePercent != null) {
            change = price.multiply(changePercent)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(currency);
        holding.setAsOf(asOf);
        holding.setName(goldHoldingDisplayName(upper));
        holding.setChange(change);
        holding.setChangePercent(changePercent);
        holding.setDayHigh(high);
        holding.setDayLow(low);

        // 52 hafta + MA20/MA50
        applyHistoryMetrics(holding, upper, spot.getUsdTry());

        // Volume: BIST altın hacmini quantityKg üzerinden doldur (Long'a çevir)
        if (spot.getQuantityKg() != null) {
            holding.setVolume(spot.getQuantityKg().longValue());
        }
    }

    static String goldHoldingDisplayName(String upper) {
        return switch (upper) {
            case "GOLD" -> "Altın (Ons)";
            case "GRAM" -> "Gram Altın";
            case "14AYAR", "AYAR14" -> "14 Ayar Bilezik";
            case "22AYAR", "AYAR22" -> "22 Ayar Bilezik";
            case "CEYREK" -> "Çeyrek Altın";
            case "YARIM" -> "Yarım Altın";
            case "TAM", "ZIYNET" -> "Tam Altın";
            case "CUMHUR", "ATA" -> "Cumhuriyet Altını";
            default -> "Altın (" + upper + ")";
        };
    }

    private void applyHistoryMetrics(PortfolioHoldingResponse holding, String upper, BigDecimal usdTry) {
        try {
            List<BigDecimal> series = buildPriceSeries(upper, usdTry);
            if (series.isEmpty()) {
                return;
            }
            holding.setFiftyTwoWeekHigh(series.stream().max(BigDecimal::compareTo).orElse(null));
            holding.setFiftyTwoWeekLow(series.stream().min(BigDecimal::compareTo).orElse(null));
            applyMasFromCloses(holding, series);
        } catch (Exception e) {
            log.debug("Gold history metrics unavailable for {}: {}", upper, e.getMessage());
        }
    }

    /**
     * Altın sembolüne göre 1Y kapanış serisi (mevcut fiyat birimiyle uyumlu).
     * GOLD → TRY/ons; GRAM ve türevleri → gram TRY veya teorik çarpan.
     */
    private List<BigDecimal> buildPriceSeries(String upper, BigDecimal usdTryFromSpot) {
        if ("GOLD".equals(upper)) {
            BigDecimal usdTry = usdTryFromSpot != null ? usdTryFromSpot : fetchUsdTryRate();
            if (usdTry == null) {
                return List.of();
            }
            GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "USD");
            if (hist == null || hist.getPoints() == null) {
                return List.of();
            }
            return hist.getPoints().stream()
                    .map(GoldHistoryPoint::getClose)
                    .filter(Objects::nonNull)
                    .map(c -> c.multiply(usdTry).setScale(2, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        }
        GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "TRY");
        if (hist == null || hist.getPoints() == null) {
            return List.of();
        }
        List<BigDecimal> gramCloses = hist.getPoints().stream()
                .map(GoldHistoryPoint::getClose)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BigDecimal factor = theoryFactor(upper);
        if (factor == null) {
            return gramCloses;
        }
        return gramCloses.stream()
                .map(g -> g.multiply(factor).setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
    }

    /** GOLD/ons history için TRY çarpanı; spot null verdiyse fallback olarak TCMB'den çek. */
    private BigDecimal fetchUsdTryRate() {
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            return latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItem::getSell)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("USD/TRY rate unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static BigDecimal theoryFactor(String upper) {
        return switch (upper) {
            case "GRAM" -> null;
            case "14AYAR", "AYAR14" -> FINENESS_14K;
            case "22AYAR", "AYAR22" -> FINENESS_22K;
            case "CEYREK" -> GROSS_QUARTER.multiply(FINENESS_22K);
            case "YARIM" -> GROSS_HALF.multiply(FINENESS_22K);
            case "TAM", "ZIYNET" -> GROSS_ZIYNET.multiply(FINENESS_22K);
            case "CUMHUR", "ATA" -> GROSS_REPUBLIC.multiply(FINENESS_22K);
            default -> null;
        };
    }
}
