package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.StressTest;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tarihsel stres testi: portföyün MEVCUT varlık-tipi dağılımını gerçek kriz dönemlerine uygular
 * ("2018 kur krizinde portföyün ~%X kaybederdi"). Varlık-bazlı kesin geçmiş çoğu enstrümanda
 * yok (o tarihte mevcut değildi) → her tip, o dönemde gerçek hareket eden bir PROXY ile temsil
 * edilir (hisse/fon→BIST100, kripto→BTC, döviz→USD/TRY, altın/emtia→gram altın, tahvil→sabit).
 *
 * <p>TL portföyü açısından kur krizinde USD/altın KAZANDIRIR (TL düşer), hisse KAYBETTİRİR →
 * proxy yaklaşımı bu dengeyi yakalar. Kriz faktörleri tarihsel sabit olduğundan kalıcı cache'lenir;
 * çekimler paralel; veri yoksa o tip dışlanıp kısmi-kapsama notu düşülür (graceful degrade).
 */
@Service
public class PortfolioStressTestService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioStressTestService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private record Crisis(String key, String label, String period, LocalDate start, LocalDate end) {}

    private static final List<Crisis> CRISES = List.of(
            new Crisis("crisis2008", "2008 Küresel Finans Krizi", "Eyl 2008 – Mar 2009",
                    LocalDate.of(2008, 9, 1), LocalDate.of(2009, 3, 2)),
            new Crisis("crisis2018", "2018 TL Kur Krizi", "Ağu – Eyl 2018",
                    LocalDate.of(2018, 8, 1), LocalDate.of(2018, 9, 14)),
            new Crisis("covid2020", "Mart 2020 Covid Çöküşü", "Şub – Mar 2020",
                    LocalDate.of(2020, 2, 19), LocalDate.of(2020, 3, 23)),
            new Crisis("crisis2022", "2022 Faiz Şoku & Kripto Kışı", "Oca – Kas 2022",
                    LocalDate.of(2022, 1, 3), LocalDate.of(2022, 11, 21)));

    /**
     * Küratörlü tarihsel kriz faktörleri (varlık tipi → fiyat[bitiş]/fiyat[başlangıç]), TL bazlı yatırımcı
     * gözünden. Canlı proxy verisi olmayan eski dönemleri kapatır → kapsam ~%100. Stres testinin amacı
     * "bu ŞOK bugünkü dağılıma çarpsa" olduğundan enstrümanın o tarihte var olup olmaması ALAKASIZ; tipi
     * temsil eden tarihsel hareket uygulanır. TL kriz dönemlerinde döviz/altın TL'ye karşı KAZANDIRIR.
     * Tek dürüst istisna: kripto 2008'de yok → tabloda yok → hariç tutulur.
     */
    private static final Map<String, Map<String, Double>> FALLBACK_FACTORS = Map.of(
            "crisis2008", Map.of(
                    "STOCK", 0.50, "FUND", 0.72, "FUTURE", 0.40, "FX", 1.30,
                    "GOLD", 1.40, "SILVER", 1.10, "COMMODITY", 0.55, "BOND", 0.96),
            "crisis2018", Map.of(
                    "STOCK", 0.83, "FUND", 0.92, "FUTURE", 0.72, "FX", 1.28, "GOLD", 1.27,
                    "SILVER", 1.18, "COMMODITY", 1.18, "BOND", 0.90, "CRYPTO", 0.92),
            "covid2020", Map.of(
                    "STOCK", 0.75, "FUND", 0.88, "FUTURE", 0.62, "FX", 1.08, "GOLD", 1.04,
                    "SILVER", 0.72, "COMMODITY", 0.45, "BOND", 0.97, "CRYPTO", 0.53),
            // 2022 faiz şoku & kripto kışı (TL bazlı): kripto sert düştü, TL-hedge (döviz/altın/BIST) yükseldi.
            "crisis2022", Map.of(
                    "STOCK", 1.50, "FUND", 1.15, "FUTURE", 1.35, "FX", 1.45, "GOLD", 1.50,
                    "SILVER", 1.30, "COMMODITY", 1.25, "BOND", 0.92, "CRYPTO", 0.48));

    private final PortfolioHistoricalPricePort pricePort;
    /** Kriz faktörleri tarihsel sabit → kalıcı cache (anahtar: crisisKey + proxy). */
    private final Map<String, Double> factorCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "stress-test");
        t.setDaemon(true);
        return t;
    });

    public PortfolioStressTestService(PortfolioHistoricalPricePort pricePort) {
        this.pricePort = pricePort;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * @param typeWeights varlık tipi (enum adı) → portföy ağırlığı (kesir, 0-1)
     */
    public List<StressTest> compute(Map<String, Double> typeWeights) {
        if (typeWeights == null || typeWeights.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<StressTest>> futures = new ArrayList<>();
        for (Crisis c : CRISES) {
            futures.add(CompletableFuture.supplyAsync(() -> computeCrisis(c, typeWeights), executor));
        }
        List<StressTest> out = new ArrayList<>();
        for (CompletableFuture<StressTest> f : futures) {
            try {
                out.add(f.join());
            } catch (Exception e) {
                log.debug("Stres testi hesaplanamadı: {}", e.getMessage());
            }
        }
        return out;
    }

    private StressTest computeCrisis(Crisis c, Map<String, Double> typeWeights) {
        double impact = 0;       // Σ weight × (factor − 1)
        double covered = 0;      // kapsanan ağırlık
        for (Map.Entry<String, Double> e : typeWeights.entrySet()) {
            String type = e.getKey();
            double w = e.getValue() != null ? e.getValue() : 0;
            if (w <= 0) {
                continue;
            }
            Double factor = proxyFactor(c, type);
            if (factor == null) {
                continue; // bu tip o dönemde temsil edilemiyor (ör. kripto 2008) → dışla
            }
            impact += w * (factor - 1);
            covered += w;
        }
        if (covered <= 0) {
            return new StressTest(c.key(), c.label(), c.period(), null,
                    "Bu kriz için portföydeki varlıklar temsil edilemedi (o dönemde mevcut değildi).", false);
        }
        String note = covered < 0.999
                ? String.format("Portföyün %%%.0f'i kapsandı (kalanı o dönemde yoktu).", covered * 100)
                : null;
        BigDecimal pct = BigDecimal.valueOf(impact * 100).setScale(2, RoundingMode.HALF_UP);
        return new StressTest(c.key(), c.label(), c.period(), pct, note, true);
    }

    /**
     * Tipin o krizdeki faktörü (fiyat[bitiş]/fiyat[başlangıç]). ÖNCE canlı proxy verisi (en doğru),
     * yoksa küratörlü tarihsel tablo (veri boşluğunu kapatır → kapsam ~%100). Yalnız kripto+2008'de
     * dürüst analog yok → null (şeffafça hariç tutulur).
     */
    private Double proxyFactor(Crisis c, String type) {
        if ("CRYPTO".equals(type) && "crisis2008".equals(c.key())) {
            return null; // kripto 2008'de yok ve dürüst bir karşılığı da yok → hariç
        }
        // 1) Canlı proxy verisi (varsa).
        String[] proxy = proxyOf(type);
        if (proxy != null) {
            String cacheKey = c.key() + ":" + proxy[1];
            Double cached = factorCache.get(cacheKey);
            Double live = (cached != null)
                    ? (cached == Double.NEGATIVE_INFINITY ? null : cached)
                    : fetchAndCache(c, proxy);
            if (live != null) {
                return live;
            }
        }
        // 2) Küratörlü tarihsel faktör (tip her durumda temsil edilir).
        Map<String, Double> table = FALLBACK_FACTORS.get(c.key());
        return table != null ? table.get(type) : null;
    }

    private Double fetchAndCache(Crisis c, String[] proxy) {
        Double factor = fetchFactor(proxy[0], proxy[1], c.start(), c.end());
        factorCache.put(c.key() + ":" + proxy[1], factor != null ? factor : Double.NEGATIVE_INFINITY);
        return factor;
    }

    private Double fetchFactor(String assetType, String symbol, LocalDate start, LocalDate end) {
        try {
            AssetType type = AssetType.valueOf(assetType);
            NavigableMap<LocalDate, BigDecimal> series = pricePort
                    .fetchDailyClosePrices(type, symbol, start.minusDays(15), end.plusDays(7)).orElse(null);
            if (series == null || series.isEmpty()) {
                return null;
            }
            var s = series.floorEntry(start);
            if (s == null) {
                s = series.firstEntry();
            }
            var en = series.floorEntry(end);
            if (s == null || en == null || s.getValue() == null || en.getValue() == null
                    || s.getValue().signum() <= 0) {
                return null;
            }
            return en.getValue().doubleValue() / s.getValue().doubleValue();
        } catch (Exception e) {
            log.debug("Stres proxy fiyatı alınamadı {} {}: {}", assetType, symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Varlık tipi → o dönemde gerçek hareket eden CANLI temsilci [assetType, symbol]. Veri varsa kullanılır,
     * yoksa {@code proxyFactor} küratörlü tabloya düşer. COMMODITY/BOND/SILVER/FUND için iyi bir derin-geçmiş
     * canlı temsilci yok → null döner ve doğrudan tarihsel tablo kullanılır (ör. petrol ≠ altın).
     */
    private static String[] proxyOf(String type) {
        return switch (type) {
            case "STOCK", "FUTURE" -> new String[]{"STOCK", "XU100.IS"};
            case "CRYPTO" -> new String[]{"CRYPTO", "BTC"};
            case "FX" -> new String[]{"FX", "USD"};
            case "GOLD" -> new String[]{"GOLD", "GRAM"};
            default -> null; // FUND/COMMODITY/SILVER/BOND/bilinmeyen → tarihsel tabloya düşer
        };
    }
}
