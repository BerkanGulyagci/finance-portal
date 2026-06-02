package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.MonteCarlo;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.ProjectionPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo / lognormal projeksiyon — portföyün gelecek değer aralığını GBM (geometrik Brownian
 * hareket) altında üretir: μ = yıllıklandırılmış getiri, σ = yıllık volatilite.
 *
 * <p><b>Neden simülasyon değil kapalı-form?</b> GBM altında {@code ln(S_t/S_0) ~ N((μ−σ²/2)t, σ²t)};
 * yani sonsuz-yol Monte Carlo'nun tam sonucu lognormal dağılımdır. Yüzdelik bantları (p5..p95) bu
 * dağılımdan ANALİTİK çıkarırız → RNG yok, seed yok, AYNI portföy → AYNI sonuç (analiz motorunun
 * determinizm ilkesiyle uyumlu) ve cache-hash kararlı.
 *
 * <p>Aşırı geçmiş getiri kısa pencerede uçabildiği için drift makul banda kıstlanır; bu durumda not düşülür.
 */
@Service
public class PortfolioMonteCarloService {

    /** Standart normal yüzdelikler için z değerleri (p5, p25, p50, p75, p95). */
    private static final double Z5 = -1.6448536;
    private static final double Z25 = -0.6744898;
    private static final double Z75 = 0.6744898;
    private static final double Z95 = 1.6448536;

    /** Drift (μ) makul yıllık banda kıstlanır — kısa geçmişten gelen uç değerler hokey-sopası üretmesin. */
    private static final double DRIFT_FLOOR = -0.60;
    private static final double DRIFT_CAP = 1.00;
    /** Volatilite tabanı — sıfıra yakın σ dejenere (sıfır-genişlik) bant üretmesin. */
    private static final double MIN_SIGMA = 0.02;

    /**
     * @param startValue            bugünkü portföy değeri (TL)
     * @param annualReturnPercent   yıllıklandırılmış getiri % (μ kaynağı)
     * @param annualVolatilityPercent yıllık volatilite % (σ kaynağı)
     * @param horizonMonths         projeksiyon ufku (ay)
     */
    public MonteCarlo project(BigDecimal startValue, BigDecimal annualReturnPercent,
                              BigDecimal annualVolatilityPercent, int horizonMonths) {
        int horizon = Math.max(1, horizonMonths);
        if (startValue == null || startValue.signum() <= 0
                || annualVolatilityPercent == null || annualReturnPercent == null) {
            return unavailable(horizon, startValue, "Projeksiyon için yeterli geçmiş/metrik yok.");
        }

        double s0 = startValue.doubleValue();
        double sigma = Math.max(annualVolatilityPercent.doubleValue() / 100.0, MIN_SIGMA);

        double rawMu = annualReturnPercent.doubleValue() / 100.0;
        double mu = clamp(rawMu, DRIFT_FLOOR, DRIFT_CAP);
        boolean clamped = Math.abs(mu - rawMu) > 1e-9;

        double drift = mu - 0.5 * sigma * sigma;

        List<ProjectionPoint> bands = new ArrayList<>(horizon + 1);
        bands.add(new ProjectionPoint(0, money(s0), money(s0), money(s0), money(s0), money(s0)));
        for (int month = 1; month <= horizon; month++) {
            double t = month / 12.0;
            double m = drift * t;
            double sd = sigma * Math.sqrt(t);
            bands.add(new ProjectionPoint(month,
                    money(s0 * Math.exp(m + Z5 * sd)),
                    money(s0 * Math.exp(m + Z25 * sd)),
                    money(s0 * Math.exp(m)),
                    money(s0 * Math.exp(m + Z75 * sd)),
                    money(s0 * Math.exp(m + Z95 * sd))));
        }

        double tH = horizon / 12.0;
        double mH = drift * tH;
        double sdH = sigma * Math.sqrt(tH);
        double median = s0 * Math.exp(mH);
        double p5 = s0 * Math.exp(mH + Z5 * sdH);
        double p95 = s0 * Math.exp(mH + Z95 * sdH);
        double medianReturn = (Math.exp(mH) - 1.0) * 100.0;
        // P(S_T < S_0) = Φ(−drift·T / (σ√T))
        double probLoss = normalCdf(-mH / sdH) * 100.0;

        String note = "Geçmiş getiri (%" + round1(rawMu * 100) + ") ve volatilitenin (%"
                + round1(sigma * 100) + ") süreceği VARSAYIMIYLA " + horizon + " aylık projeksiyon; kesin tahmin değildir."
                + (clamped ? " Aşırı drift makul banda (%" + round1(mu * 100) + ") kıstlandı." : "");

        return new MonteCarlo(horizon, true, note, bands,
                money(s0), money(median), money(p5), money(p95),
                pct(medianReturn), pct(probLoss),
                pct(mu * 100), pct(sigma * 100));
    }

    private static MonteCarlo unavailable(int horizon, BigDecimal startValue, String note) {
        return new MonteCarlo(horizon, false, note, List.of(),
                startValue, null, null, null, null, null, null, null);
    }

    /** Normal kümülatif dağılım Φ(x) — Abramowitz & Stegun 7.1.26 erf yaklaşımı. */
    private static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double sign = Math.signum(x);
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t * Math.exp(-ax * ax);
        return sign * y;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
