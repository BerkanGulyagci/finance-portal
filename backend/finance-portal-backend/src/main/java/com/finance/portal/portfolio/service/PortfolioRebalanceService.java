package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Rebalance;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RebalanceItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yeniden dengeleme (rebalancing) — EĞİTSEL amaçlı, yatırım tavsiyesi DEĞİL. Seçilen risk profiline ait
 * örnek bir hedef tip-dağılımı ile portföyün MEVCUT dağılımını karşılaştırır, tip-bazında sapmayı ve
 * yönü (artır/azalt/koru) çıkarır. Hedef şablonlar TL bazlı yatırımcı için tipik dağılımlardır; kişiye
 * özel öneri değildir — kullanıcının kendi kararına yardımcı bir referanstır.
 */
@Service
public class PortfolioRebalanceService {

    /** Tip bazında "koru" sayılacak |sapma| eşiği (%). */
    private static final double KEEP_BAND = 2.0;

    /** Profil → örnek hedef tip-dağılımı (tip enum adı → %). Her biri 100'e toplanır. */
    private static final Map<String, Map<String, Double>> TARGETS = Map.of(
            "CONSERVATIVE", Map.of("BOND", 40.0, "GOLD", 20.0, "FX", 15.0, "FUND", 15.0, "STOCK", 10.0),
            "BALANCED", Map.of("STOCK", 35.0, "BOND", 25.0, "GOLD", 15.0, "FUND", 15.0, "FX", 5.0, "CRYPTO", 5.0),
            "AGGRESSIVE", Map.of("STOCK", 40.0, "CRYPTO", 20.0, "FUND", 15.0, "COMMODITY", 10.0, "GOLD", 10.0, "FX", 5.0));

    private static final Map<String, String> PROFILE_LABELS = Map.of(
            "CONSERVATIVE", "Korumacı", "BALANCED", "Dengeli", "AGGRESSIVE", "Agresif");

    /**
     * @param currentByType mevcut dağılım: tip enum adı → ağırlık % (toplam ≈ 100)
     * @param profile       hedef profil (CONSERVATIVE/BALANCED/AGGRESSIVE; bilinmiyorsa BALANCED)
     */
    public Rebalance compute(Map<String, BigDecimal> currentByType, String profile) {
        String prof = TARGETS.containsKey(profile) ? profile : "BALANCED";
        Map<String, Double> target = TARGETS.get(prof);

        // Mevcut + hedef tiplerin birleşimi (hedef sırası önce, sonra kalan mevcut tipler).
        Set<String> types = new LinkedHashSet<>(target.keySet());
        if (currentByType != null) {
            types.addAll(currentByType.keySet());
        }

        List<RebalanceItem> items = new ArrayList<>();
        double driftSum = 0;
        for (String type : types) {
            double cur = currentByType != null && currentByType.get(type) != null
                    ? currentByType.get(type).doubleValue() : 0.0;
            double tgt = target.getOrDefault(type, 0.0);
            double delta = tgt - cur;
            driftSum += Math.abs(delta);
            String action = delta > KEEP_BAND ? "ARTIR" : delta < -KEEP_BAND ? "AZALT" : "KORU";
            items.add(new RebalanceItem(type, typeLabel(type), pct(cur), pct(tgt), pct(delta), action));
        }
        // En büyük sapmalar üstte.
        items.sort((a, b) -> Double.compare(Math.abs(b.deltaPercent().doubleValue()),
                Math.abs(a.deltaPercent().doubleValue())));

        BigDecimal drift = pct(driftSum / 2.0); // mutlak sapma toplamı / 2 = "devir" oranı (0-100)
        String note = "\"" + PROFILE_LABELS.get(prof) + "\" profili için örnek hedef dağılımdır; "
                + "kişiye özel yatırım tavsiyesi değildir.";
        return new Rebalance(prof, PROFILE_LABELS.get(prof), items, drift, note);
    }

    public boolean isKnownProfile(String profile) {
        return TARGETS.containsKey(profile);
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "STOCK" -> "Hisse";
            case "CRYPTO" -> "Kripto";
            case "FUND" -> "Fon";
            case "BOND" -> "Tahvil";
            case "GOLD" -> "Altın";
            case "SILVER" -> "Gümüş";
            case "COMMODITY" -> "Emtia";
            case "FX" -> "Döviz";
            case "FUTURE" -> "Vadeli (VİOP)";
            default -> type;
        };
    }
}
