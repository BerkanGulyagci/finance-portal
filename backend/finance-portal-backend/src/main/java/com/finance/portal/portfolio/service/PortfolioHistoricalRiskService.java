package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
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
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Risk metriklerini portföyün MEVCUT ağırlıklarını VARLIKLARIN KENDİ fiyat geçmişine uygulayarak
 * hesaplar — kullanıcının elde tutma süresinden BAĞIMSIZ. "Bu dağılım son N ay tutulsaydı volatilitesi/
 * Sharpe'ı/max düşüşü ne olurdu" sorusunu yanıtlar. Böylece yeni açılmış portföylerde bile metrik üretilir
 * (eski actual/cost oran yöntemi ≥5 aylık KULLANICI geçmişi istiyordu → çoğu portföyde boş kalıyordu).
 *
 * <p>Yaklaşım stres testiyle aynı: her varlığın gerçek fiyat serisi {@link PortfolioHistoricalPricePort}'tan
 * çekilir, aylık örneklenir, ağırlıkla birleştirilip sentetik portföy getiri serisi kurulur. Fiyatı olmayan
 * varlık o ay dışlanır ve kalan ağırlık yeniden normalize edilir (kapsama < %50 ise o ay atlanır).
 *
 * <p>Risksiz oran 0 varsayılır (note'ta belirtilir); ana çıktı volatilite + yıllık getiri olup Monte Carlo'yu
 * da besler. Çekimler paralel; veri yetersizse {@code available=false} (çağıran eski yönteme düşer).
 */
@Service
public class PortfolioHistoricalRiskService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHistoricalRiskService.class);

    /** Anlamlı istatistik için asgari geçerli aylık getiri sayısı. */
    private static final int MIN_SAMPLES = 5;
    /** Bir ayın sayılması için kapsanması gereken asgari ağırlık (fiyatı olan varlıklar). */
    private static final double MIN_COVERAGE = 0.5;
    private static final String BIST_SYMBOL = "XU100.IS";

    /** Tek pozisyon: tip + sembol + ağırlık (kesir 0-1). */
    public record Position(AssetType type, String symbol, double weight) {}

    private final PortfolioHistoricalPricePort pricePort;
    private final ExecutorService executor = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "hist-risk");
        t.setDaemon(true);
        return t;
    });

    public PortfolioHistoricalRiskService(PortfolioHistoricalPricePort pricePort) {
        this.pricePort = pricePort;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * @param positions mevcut pozisyonlar (tip/sembol/ağırlık)
     * @param months    geriye dönük pencere (ay) — örn. 12
     */
    public RiskMetrics computeFromHoldings(List<Position> positions, int months) {
        if (positions == null || positions.isEmpty() || months < 2) {
            return unavailable(0, "Pozisyon yok");
        }
        LocalDate today = LocalDate.now();
        List<LocalDate> anchors = new ArrayList<>();
        for (int i = months; i >= 0; i--) {
            anchors.add(today.minusMonths(i));
        }
        LocalDate from = anchors.get(0).minusDays(45);

        // Her varlığın (ve BIST'in) anchor fiyatlarını paralel örnekle.
        List<CompletableFuture<double[]>> futures = new ArrayList<>();
        for (Position p : positions) {
            futures.add(CompletableFuture.supplyAsync(() -> sampleAt(p.type(), p.symbol(), anchors, from, today), executor));
        }
        CompletableFuture<double[]> bistF =
                CompletableFuture.supplyAsync(() -> sampleAt(AssetType.STOCK, BIST_SYMBOL, anchors, from, today), executor);

        List<double[]> prices = new ArrayList<>();
        for (CompletableFuture<double[]> f : futures) {
            try {
                prices.add(f.join());
            } catch (Exception e) {
                prices.add(null);
            }
        }
        double[] bist = bistF.join();

        // Aylık sentetik portföy getirileri (kapsanan ağırlığa göre normalize).
        List<Double> portRet = new ArrayList<>();
        List<Double> bistRetAligned = new ArrayList<>();
        for (int t = 1; t < anchors.size(); t++) {
            double weighted = 0;
            double covered = 0;
            for (int a = 0; a < positions.size(); a++) {
                double[] px = prices.get(a);
                if (px == null) {
                    continue;
                }
                double w = positions.get(a).weight();
                if (px[t] > 0 && px[t - 1] > 0) {
                    weighted += w * (px[t] / px[t - 1] - 1);
                    covered += w;
                }
            }
            if (covered >= MIN_COVERAGE) {
                double r = weighted / covered;
                portRet.add(r);
                bistRetAligned.add((bist[t] > 0 && bist[t - 1] > 0) ? (bist[t] / bist[t - 1] - 1) : null);
            }
        }

        if (portRet.size() < MIN_SAMPLES) {
            return unavailable(portRet.size(), "Varlıkların fiyat geçmişi yetersiz");
        }

        int n = portRet.size();
        double years = Math.max(n / 12.0, 1.0 / 12);
        double idx = 1.0;
        for (double r : portRet) {
            idx *= (1 + r);
        }
        double annualReturn = Math.pow(Math.max(idx, 1e-6), 1.0 / years) - 1;
        double annualVol = stddev(portRet) * Math.sqrt(12);
        double sharpe = annualVol > 1e-9 ? annualReturn / annualVol : 0.0; // rf=0
        double downside = downsideDev(portRet) * Math.sqrt(12);
        double sortino = downside > 1e-9 ? annualReturn / downside : 0.0;
        double maxDd = maxDrawdownFromReturns(portRet);

        // Beta — yalnız hem portföy hem BIST getirisinin geçerli olduğu aylardan.
        List<Double> pPairs = new ArrayList<>();
        List<Double> bPairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Double br = bistRetAligned.get(i);
            if (br != null) {
                pPairs.add(portRet.get(i));
                bPairs.add(br);
            }
        }
        Double beta = null;
        if (pPairs.size() >= MIN_SAMPLES) {
            double varB = variance(bPairs);
            if (varB > 1e-12) {
                beta = covariance(pPairs, bPairs) / varB;
            }
        }

        String note = "Mevcut dağılımın son " + n + " ayki tarihsel davranışı (hipotetik; risksiz oran %0).";
        return new RiskMetrics(pct(annualReturn * 100), pct(annualVol * 100), num(sharpe), num(sortino),
                pct(maxDd * 100), beta != null ? num(beta) : null, n, true, note);
    }

    // ── Fiyat örnekleme ─────────────────────────────────────────────────────────

    private double[] sampleAt(AssetType type, String symbol, List<LocalDate> anchors, LocalDate from, LocalDate to) {
        double[] out = new double[anchors.size()];
        try {
            NavigableMap<LocalDate, BigDecimal> s = pricePort.fetchDailyClosePrices(type, symbol, from, to).orElse(null);
            if (s == null || s.isEmpty()) {
                return out; // hepsi 0 → o varlık dışlanır
            }
            for (int i = 0; i < anchors.size(); i++) {
                var e = s.floorEntry(anchors.get(i));
                out[i] = (e != null && e.getValue() != null && e.getValue().signum() > 0) ? e.getValue().doubleValue() : 0;
            }
        } catch (Exception ex) {
            log.debug("Tarihsel risk fiyatı alınamadı {} {}: {}", type, symbol, ex.getMessage());
        }
        return out;
    }

    private static RiskMetrics unavailable(int samples, String note) {
        return new RiskMetrics(null, null, null, null, null, null, samples, false, note);
    }

    // ── İstatistik ──────────────────────────────────────────────────────────────

    private static double mean(List<Double> xs) {
        if (xs.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.size();
    }

    private static double variance(List<Double> xs) {
        if (xs.size() < 2) {
            return 0;
        }
        double m = mean(xs);
        double s = 0;
        for (double x : xs) {
            s += (x - m) * (x - m);
        }
        return s / (xs.size() - 1);
    }

    private static double stddev(List<Double> xs) {
        return Math.sqrt(variance(xs));
    }

    private static double downsideDev(List<Double> xs) {
        double s = 0;
        int n = 0;
        for (double x : xs) {
            if (x < 0) {
                s += x * x;
                n++;
            }
        }
        return n > 0 ? Math.sqrt(s / n) : 0;
    }

    private static double covariance(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n < 2) {
            return 0;
        }
        double ma = mean(a);
        double mb = mean(b);
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += (a.get(i) - ma) * (b.get(i) - mb);
        }
        return s / (n - 1);
    }

    /** Aylık getirilerden kümülatif indeks kurup tepe-dip max düşüşü (0-1). */
    private static double maxDrawdownFromReturns(List<Double> rets) {
        double idx = 1.0;
        double peak = 1.0;
        double maxDd = 0;
        for (double r : rets) {
            idx *= (1 + r);
            if (idx > peak) {
                peak = idx;
            }
            if (peak > 1e-9) {
                double dd = (peak - idx) / peak;
                if (dd > maxDd) {
                    maxDd = dd;
                }
            }
        }
        return maxDd;
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal num(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
