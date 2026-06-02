package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AllocationSlice;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetForecast;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetReturn;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetSignal;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.BenchmarkItem;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Forecast;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.ForecastHorizon;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Classification;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Concentration;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.ScoreFactor;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.SeriesPoint;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesPoint;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * AI Portföy Analizi metrik motoru — TÜM sayıları DETERMİNİSTİK olarak burada hesaplar
 * (aynı portföy → aynı skor). Yorum/hikaye {@code PortfolioAiNarrator} (LLM) işidir.
 *
 * <p>Veri kaynakları (yeniden kullanım):
 * <ul>
 *   <li>{@link PortfolioService#getPortfolioById} → holdings + toplamlar (ownership kontrolü dahil)</li>
 *   <li>{@link PortfolioWhatIfService#computeSeries} → aylık DEĞER + benchmark serileri
 *       (gerçek/bist100/gold/usd/deposit/sp500/enflasyon) — risk metrikleri bundan</li>
 * </ul>
 *
 * <p><b>Risk metrikleri (volatilite/Sharpe/Sortino/drawdown/beta)</b> ham {@code actual} yerine
 * {@code actual/cost} ORAN serisinden hesaplanır: para yatırma (katkı) hem actual hem cost'u aynı
 * anda artırdığından oran sürekli kalır → getiri ölçümü katkıdan arındırılmış (time-weighted yaklaşık).
 */
@Service
public class PortfolioAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAnalysisService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 2;
    /** Risk metrikleri için anlamlı istatistik gereken asgari aylık nokta sayısı. */
    private static final int MIN_SAMPLES = 5;
    private static final int TOP_N = 5;

    private final PortfolioService portfolioService;
    private final PortfolioWhatIfService whatIfService;
    private final PortfolioCurrencyConverter currencyConverter;
    private final PortfolioStressTestService stressTestService;
    private final PortfolioHistoricalRiskService historicalRiskService;
    private final PortfolioMonteCarloService monteCarloService;
    private final PortfolioRebalanceService rebalanceService;
    private final PortfolioAiNarrator narrator;

    public PortfolioAnalysisService(PortfolioService portfolioService,
                                    PortfolioWhatIfService whatIfService,
                                    PortfolioCurrencyConverter currencyConverter,
                                    PortfolioStressTestService stressTestService,
                                    PortfolioHistoricalRiskService historicalRiskService,
                                    PortfolioMonteCarloService monteCarloService,
                                    PortfolioRebalanceService rebalanceService,
                                    PortfolioAiNarrator narrator) {
        this.portfolioService = portfolioService;
        this.whatIfService = whatIfService;
        this.currencyConverter = currencyConverter;
        this.stressTestService = stressTestService;
        this.historicalRiskService = historicalRiskService;
        this.monteCarloService = monteCarloService;
        this.rebalanceService = rebalanceService;
        this.narrator = narrator;
    }

    /**
     * Yalnız yeniden dengeleme hesaplar (tam analizi çalıştırmadan): mevcut tip-dağılımını çıkarıp
     * seçilen profile göre örnek hedefle karşılaştırır. Risk-profili anketi farklı profil seçince çağrılır.
     */
    public PortfolioAiAnalysisResult.Rebalance rebalanceFor(String userId, UUID portfolioId, String profile) {
        PortfolioResponse resp = portfolioService.getPortfolioById(userId, portfolioId); // ownership
        List<PortfolioHoldingResponse> holdings = (resp != null && resp.getHoldings() != null)
                ? resp.getHoldings() : List.of();
        Map<String, BigDecimal> byTypeTl = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioHoldingResponse h : holdings) {
            if (h.isClosed()) {
                continue;
            }
            BigDecimal mvTry = currencyConverter.toTry(h.getMarketValue(), h.getCurrency());
            if (mvTry == null || mvTry.signum() <= 0) {
                continue;
            }
            byTypeTl.merge(typeName(h.getAssetType()), mvTry, BigDecimal::add);
            total = total.add(mvTry);
        }
        Map<String, BigDecimal> byTypePct = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : byTypeTl.entrySet()) {
            byTypePct.put(e.getKey(), pctOf(e.getValue(), total));
        }
        return rebalanceService.compute(byTypePct, profile);
    }

    public PortfolioAiAnalysisResult analyze(String userId, UUID portfolioId, String userName, String userEmail) {
        PortfolioResponse resp = portfolioService.getPortfolioById(userId, portfolioId); // ownership + enrich
        PortfolioAiAnalysisResult r = new PortfolioAiAnalysisResult();
        r.setPortfolioId(portfolioId);
        r.setName(resp != null ? resp.getName() : null);
        r.setAsOf(LocalDate.now());
        List<String> notes = new ArrayList<>();
        r.setNotes(notes);

        List<PortfolioHoldingResponse> holdings = (resp != null && resp.getHoldings() != null)
                ? resp.getHoldings() : List.of();
        // Açık (kapanmamış) ve TL değeri olan pozisyonlar.
        List<HoldingTry> rows = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (PortfolioHoldingResponse h : holdings) {
            if (h.isClosed()) {
                continue;
            }
            BigDecimal mvTry = currencyConverter.toTry(h.getMarketValue(), h.getCurrency());
            BigDecimal costTry = currencyConverter.toTry(h.getTotalCost(), h.getCurrency());
            if (mvTry == null || mvTry.signum() <= 0) {
                continue;
            }
            BigDecimal plTry = currencyConverter.toTry(h.getProfitLoss(), h.getCurrency());
            rows.add(new HoldingTry(h, mvTry, costTry, plTry));
            totalValue = totalValue.add(mvTry);
        }

        r.setHoldingsCount(rows.size());
        r.setTotalValueTry(scale(totalValue));
        r.setTotalCostTry(resp != null ? resp.getTotalCost() : null);
        r.setTotalProfitLossTry(resp != null ? resp.getTotalProfitLoss() : null);
        r.setTotalProfitLossPercent(profitPercent(resp));
        r.setRealProfitLossPercent(resp != null ? resp.getTotalRealProfitLossPercent() : null);

        if (rows.isEmpty() || totalValue.signum() <= 0) {
            notes.add("Analiz için açık pozisyon bulunamadı.");
            r.setRiskMetrics(new RiskMetrics(null, null, null, null, null, null, 0, false, "Pozisyon yok"));
            return r;
        }

        // ── Dağılım + yoğunlaşma + varlık getirileri ────────────────────────────
        List<AllocationSlice> allocation = buildAllocation(rows, totalValue);
        r.setAllocation(allocation);
        r.setAssetTypeAllocation(buildAssetTypeAllocation(rows, totalValue));
        Concentration concentration = buildConcentration(allocation);
        r.setConcentration(concentration);
        List<AssetReturn> assetReturns = buildAssetReturns(rows, totalValue);
        r.setAssetReturns(assetReturns);
        r.setTopGainers(topBy(assetReturns, true));
        r.setTopLosers(topBy(assetReturns, false));

        // ── Zaman serisi (değer + benchmark) → risk metrikleri + benchmark + reel ─
        WhatIfSeriesResult series = safeSeries(resp);
        List<WhatIfSeriesPoint> pts = (series != null && series.getPoints() != null)
                ? series.getPoints() : List.of();
        r.setValueSeries(buildValueSeries(pts));
        // Risk metrikleri: ÖNCE varlıkların kendi fiyat geçmişinden (kullanıcı elde-tutma süresinden bağımsız,
        // yeni portföylerde de çalışır); yetersizse eski actual/cost oran yöntemine düş.
        RiskMetrics metrics = historicalRiskService.computeFromHoldings(buildPositions(rows, totalValue), 12);
        if (metrics == null || !metrics.available()) {
            metrics = computeRiskMetrics(pts, notes);
        }
        r.setRiskMetrics(metrics);
        r.setBenchmarks(buildBenchmarks(pts));
        r.setInflationSincePercent(benchmarkReturn(pts, WhatIfSeriesPoint::getInflation));

        // ── Tarihsel stres testleri (mevcut dağılımı 2008/2018/2020 krizlerine uygula) ─
        try {
            r.setStressTests(stressTestService.compute(typeWeightFractions(rows, totalValue)));
        } catch (Exception e) {
            log.warn("Stres testleri hesaplanamadı (degrade): {}", e.getMessage());
            r.setStressTests(List.of());
        }

        // ── Skorlar (şeffaf formül) ─────────────────────────────────────────────
        computeScores(r, rows, totalValue, concentration, metrics);

        // ── Portföy kimliği + varlık-bazlı sinyaller + Monte Carlo projeksiyon ──
        r.setClassification(buildClassification(rows, totalValue, r.getRiskScore()));
        r.setAssetSignals(buildAssetSignals(rows, totalValue));
        r.setMonteCarlo(monteCarloService.project(r.getTotalValueTry(),
                metrics != null ? metrics.annualReturnPercent() : null,
                metrics != null ? metrics.annualVolatilityPercent() : null, 12));
        // Yeniden dengeleme: tespit edilen profile göre örnek hedef vs mevcut (kullanıcı profili anketle değiştirebilir).
        r.setRebalance(rebalanceService.compute(currentTypePercents(r),
                r.getClassification() != null ? r.getClassification().profile() : "BALANCED"));
        // Çok-ufuklu tahmin (1ay/3ay/1y): kod aralık üretir, AI yön/anlatı yorumlar.
        r.setForecast(buildForecast(rows, totalValue, metrics, r.getAssetSignals()));

        // ── AI yorum (graceful degrade) ─────────────────────────────────────────
        try {
            String report = narrator.generate(r, userId, userName, userEmail);
            r.setAiReport(report);
            r.setAiReportAvailable(report != null && !report.isBlank());
        } catch (Exception e) {
            log.warn("AI analiz yorumu üretilemedi (degrade): {}", e.getMessage());
            r.setAiReport(null);
            r.setAiReportAvailable(false);
            notes.add("AI yorumu şu an kullanılamıyor; metrikler ve grafikler geçerlidir.");
        }

        notes.add("Bu analiz bilgilendirme amaçlıdır, yatırım tavsiyesi değildir.");
        return r;
    }

    // ── Dağılım ─────────────────────────────────────────────────────────────────

    private List<AllocationSlice> buildAllocation(List<HoldingTry> rows, BigDecimal total) {
        List<AllocationSlice> out = new ArrayList<>();
        for (HoldingTry row : rows) {
            PortfolioHoldingResponse h = row.h();
            String label = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();
            out.add(new AllocationSlice(label, typeName(h.getAssetType()),
                    scale(row.mvTry()), pctOf(row.mvTry(), total)));
        }
        out.sort(Comparator.comparing(AllocationSlice::weightPercent, Comparator.reverseOrder()));
        return out;
    }

    private List<AllocationSlice> buildAssetTypeAllocation(List<HoldingTry> rows, BigDecimal total) {
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        for (HoldingTry row : rows) {
            byType.merge(typeName(row.h().getAssetType()), row.mvTry(), BigDecimal::add);
        }
        List<AllocationSlice> out = new ArrayList<>();
        byType.forEach((type, val) -> out.add(new AllocationSlice(typeLabel(type), type, scale(val), pctOf(val, total))));
        out.sort(Comparator.comparing(AllocationSlice::weightPercent, Comparator.reverseOrder()));
        return out;
    }

    private Concentration buildConcentration(List<AllocationSlice> allocation) {
        BigDecimal top = allocation.isEmpty() ? BigDecimal.ZERO : allocation.get(0).weightPercent();
        String topLabel = allocation.isEmpty() ? null : allocation.get(0).label();
        BigDecimal top3 = BigDecimal.ZERO;
        double hhi = 0.0;
        for (int i = 0; i < allocation.size(); i++) {
            BigDecimal w = allocation.get(i).weightPercent();
            if (i < 3) {
                top3 = top3.add(w);
            }
            double frac = w.doubleValue() / 100.0;
            hhi += frac * frac;
        }
        String label;
        double topD = top.doubleValue();
        if (topD >= 40 || hhi >= 0.4) {
            label = "Yüksek yoğunlaşma";
        } else if (topD >= 25 || hhi >= 0.25) {
            label = "Orta yoğunlaşma";
        } else {
            label = "İyi çeşitlendirilmiş";
        }
        return new Concentration(scalePct(top), scalePct(top3),
                BigDecimal.valueOf(hhi).setScale(4, RoundingMode.HALF_UP), label, topLabel);
    }

    private List<AssetReturn> buildAssetReturns(List<HoldingTry> rows, BigDecimal total) {
        List<AssetReturn> out = new ArrayList<>();
        for (HoldingTry row : rows) {
            PortfolioHoldingResponse h = row.h();
            String label = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();
            BigDecimal plPct = h.getProfitLossPercent();
            if (plPct == null && row.costTry() != null && row.costTry().signum() > 0) {
                plPct = pct(row.mvTry(), row.costTry());
            }
            out.add(new AssetReturn(h.getSymbol(), label, typeName(h.getAssetType()),
                    pctOf(row.mvTry(), total), plPct, scale(row.plTry())));
        }
        out.sort(Comparator.comparing(AssetReturn::weightPercent, Comparator.reverseOrder()));
        return out;
    }

    private List<AssetReturn> topBy(List<AssetReturn> all, boolean gainers) {
        List<AssetReturn> sorted = new ArrayList<>(all);
        sorted.removeIf(a -> a.profitLossPercent() == null);
        sorted.sort(Comparator.comparing(AssetReturn::profitLossPercent,
                gainers ? Comparator.reverseOrder() : Comparator.naturalOrder()));
        List<AssetReturn> out = new ArrayList<>();
        for (AssetReturn a : sorted) {
            if (out.size() >= TOP_N) break;
            // gainers: yalnız pozitif; losers: yalnız negatif
            int sign = a.profitLossPercent().signum();
            if (gainers && sign > 0) out.add(a);
            else if (!gainers && sign < 0) out.add(a);
        }
        return out;
    }

    // ── Portföy kimliği / sınıflandırma ─────────────────────────────────────────

    /**
     * Varlık-tipi ağırlıklarından + risk skorundan profil türetir.
     * Büyüme-odaklı: hisse/kripto/vadeli/emtia. Korumacı: tahvil/altın/döviz/gümüş. Fon = karma (sayılmaz).
     */
    private Classification buildClassification(List<HoldingTry> rows, BigDecimal total, int riskScore) {
        double t = total.doubleValue();
        double growth = 0, defensive = 0;
        if (t > 0) {
            for (HoldingTry row : rows) {
                double w = row.mvTry().doubleValue() / t * 100.0;
                switch (typeName(row.h().getAssetType())) {
                    case "STOCK", "CRYPTO", "FUTURE", "COMMODITY" -> growth += w;
                    case "BOND", "GOLD", "FX", "SILVER" -> defensive += w;
                    default -> { /* FUND/OTHER → karma, sayılmaz */ }
                }
            }
        }
        String profile;
        String label;
        String detail;
        if (growth >= 60 || riskScore >= 66) {
            profile = "AGGRESSIVE";
            label = "Agresif";
            detail = "Büyüme-odaklı varlık ağırlığı baskın; getiri potansiyeli yüksek, dalgalanma da yüksek.";
        } else if (defensive >= 55 || riskScore < 33) {
            profile = "CONSERVATIVE";
            label = "Korumacı";
            detail = "Korumacı/sabit-getirili ağırlık baskın; sermaye koruma önceliği, getiri daha ılımlı.";
        } else {
            profile = "BALANCED";
            label = "Dengeli";
            detail = "Büyüme ve korumacı varlıklar dengeli; orta risk-getiri profili.";
        }
        return new Classification(profile, label, detail,
                scalePct(BigDecimal.valueOf(growth)), scalePct(BigDecimal.valueOf(defensive)));
    }

    // ── Varlık-bazlı teknik sinyaller (holdings'te enrich edilmiş alanlardan) ────

    private List<AssetSignal> buildAssetSignals(List<HoldingTry> rows, BigDecimal total) {
        List<AssetSignal> out = new ArrayList<>();
        for (HoldingTry row : rows) {
            PortfolioHoldingResponse h = row.h();
            String label = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();

            BigDecimal price = h.getCurrentPrice();
            BigDecimal ma20 = h.getMa20();
            BigDecimal ma50 = h.getMa50();
            String trend = "NEUTRAL";
            String trendLabel = "Yatay/belirsiz";
            String maState = null;
            if (price != null && price.signum() > 0 && ma20 != null && ma50 != null
                    && ma20.signum() > 0 && ma50.signum() > 0) {
                boolean above20 = price.compareTo(ma20) >= 0;
                boolean above50 = price.compareTo(ma50) >= 0;
                if (above20 && above50) {
                    trend = "UP";
                    trendLabel = "Yükseliş eğilimi";
                    maState = "Fiyat MA20 ve MA50 üstünde";
                } else if (!above20 && !above50) {
                    trend = "DOWN";
                    trendLabel = "Düşüş eğilimi";
                    maState = "Fiyat MA20 ve MA50 altında";
                } else {
                    trend = "NEUTRAL";
                    trendLabel = "Kararsız";
                    maState = above50 ? "MA50 üstü, MA20 altı" : "MA20 üstü, MA50 altı";
                }
            }

            BigDecimal range52 = null;
            BigDecimal hi = h.getFiftyTwoWeekHigh();
            BigDecimal lo = h.getFiftyTwoWeekLow();
            if (price != null && hi != null && lo != null && hi.compareTo(lo) > 0) {
                double pos = (price.doubleValue() - lo.doubleValue()) / (hi.doubleValue() - lo.doubleValue()) * 100.0;
                range52 = scalePct(BigDecimal.valueOf(clamp(pos, 0, 100)));
            }

            BigDecimal m1 = h.getReturnOneMonth();
            BigDecimal m3 = h.getReturnThreeMonths();
            String note = buildSignalNote(trendLabel, range52, m1);

            out.add(new AssetSignal(h.getSymbol(), label, typeName(h.getAssetType()),
                    pctOf(row.mvTry(), total), trend, trendLabel, maState, range52,
                    m1, m3, h.getProfitLossPercent(), note));
        }
        out.sort(Comparator.comparing(AssetSignal::weightPercent, Comparator.reverseOrder()));
        return out;
    }

    private static String buildSignalNote(String trendLabel, BigDecimal range52, BigDecimal momentum1m) {
        StringBuilder b = new StringBuilder(trendLabel);
        if (range52 != null) {
            double pos = range52.doubleValue();
            String where = pos >= 80 ? "52-hafta zirvesine yakın" : pos <= 20 ? "52-hafta dibine yakın"
                    : pos >= 55 ? "52-hafta bandının üst yarısında" : "52-hafta bandının alt yarısında";
            b.append("; ").append(where);
        }
        if (momentum1m != null) {
            b.append("; son 1 ay %").append(momentum1m.setScale(1, RoundingMode.HALF_UP).toPlainString());
        }
        return b.toString();
    }

    // ── Risk metrikleri (actual/cost oran serisinden) ───────────────────────────

    private RiskMetrics computeRiskMetrics(List<WhatIfSeriesPoint> pts, List<String> notes) {
        List<Double> portRatio = ratioSeries(pts, WhatIfSeriesPoint::getActual);
        if (portRatio.size() < MIN_SAMPLES) {
            notes.add("Risk metrikleri için geçmiş çok kısa (en az " + MIN_SAMPLES + " aylık nokta gerekir).");
            return new RiskMetrics(null, null, null, null, null, null, portRatio.size(), false, "Geçmiş kısa");
        }
        List<Double> rPort = returns(portRatio);
        int months = rPort.size();
        double years = Math.max(months / 12.0, 1.0 / 12);

        double monthlyVol = stddev(rPort);
        double annualVol = monthlyVol * Math.sqrt(12);
        double totalGrowth = portRatio.get(portRatio.size() - 1);                 // ≈ 1 + toplam getiri
        double annualReturn = Math.pow(Math.max(totalGrowth, 0.0001), 1.0 / years) - 1;

        // Risksiz oran ≈ mevduat benchmark'ının yıllık getirisi (yoksa 0).
        List<Double> depRatio = ratioSeries(pts, WhatIfSeriesPoint::getDeposit);
        double rf = depRatio.size() >= 2
                ? Math.pow(Math.max(depRatio.get(depRatio.size() - 1), 0.0001), 1.0 / years) - 1
                : 0.0;

        double sharpe = annualVol > 1e-9 ? (annualReturn - rf) / annualVol : 0.0;
        double downside = downsideDev(rPort) * Math.sqrt(12);
        double sortino = downside > 1e-9 ? (annualReturn - rf) / downside : 0.0;
        double maxDd = maxDrawdown(portRatio);

        // Beta — BIST100 oran serisine karşı (kovaryans/varyans, aylık getirilerden).
        Double beta = null;
        List<Double> bistRatio = ratioSeries(pts, WhatIfSeriesPoint::getBist100);
        if (bistRatio.size() == portRatio.size() && bistRatio.size() >= MIN_SAMPLES) {
            List<Double> rBist = returns(bistRatio);
            double varB = variance(rBist);
            if (varB > 1e-12 && rBist.size() == rPort.size()) {
                beta = covariance(rPort, rBist) / varB;
            }
        }

        return new RiskMetrics(
                pctVal(annualReturn), pctVal(annualVol), num(sharpe), num(sortino), pctVal(maxDd),
                beta != null ? num(beta) : null, months, true, null);
    }

    // ── Benchmark ───────────────────────────────────────────────────────────────

    private List<BenchmarkItem> buildBenchmarks(List<WhatIfSeriesPoint> pts) {
        List<BenchmarkItem> out = new ArrayList<>();
        BigDecimal portRet = benchmarkReturn(pts, WhatIfSeriesPoint::getActual);
        addBenchmark(out, pts, "bist100", "BIST 100", WhatIfSeriesPoint::getBist100, portRet);
        addBenchmark(out, pts, "sp500", "S&P 500", WhatIfSeriesPoint::getSp500, portRet);
        addBenchmark(out, pts, "gold", "Altın (gram)", WhatIfSeriesPoint::getGold, portRet);
        addBenchmark(out, pts, "usd", "Dolar (USD)", WhatIfSeriesPoint::getUsd, portRet);
        addBenchmark(out, pts, "deposit", "Mevduat", WhatIfSeriesPoint::getDeposit, portRet);
        addBenchmark(out, pts, "inflation", "Enflasyon (TÜFE)", WhatIfSeriesPoint::getInflation, portRet);
        return out;
    }

    private void addBenchmark(List<BenchmarkItem> out, List<WhatIfSeriesPoint> pts, String key, String label,
                              Function<WhatIfSeriesPoint, BigDecimal> field, BigDecimal portRet) {
        BigDecimal ret = benchmarkReturn(pts, field);
        if (ret == null) {
            return;
        }
        BigDecimal delta = (portRet != null) ? scalePct(portRet.subtract(ret)) : null;
        out.add(new BenchmarkItem(key, label, ret, delta));
    }

    /** Son noktada: (seri[last]/cost[last] − 1) × 100. */
    private BigDecimal benchmarkReturn(List<WhatIfSeriesPoint> pts, Function<WhatIfSeriesPoint, BigDecimal> field) {
        for (int i = pts.size() - 1; i >= 0; i--) {
            WhatIfSeriesPoint p = pts.get(i);
            BigDecimal v = field.apply(p);
            BigDecimal c = p.getCost();
            if (v != null && v.signum() > 0 && c != null && c.signum() > 0) {
                return scalePct(pct(v, c));
            }
        }
        return null;
    }

    private List<SeriesPoint> buildValueSeries(List<WhatIfSeriesPoint> pts) {
        List<SeriesPoint> out = new ArrayList<>();
        for (WhatIfSeriesPoint p : pts) {
            out.add(new SeriesPoint(p.getDate(), p.getActual(), p.getCost()));
        }
        return out;
    }

    // ── Skorlar (şeffaf formül) ─────────────────────────────────────────────────

    private void computeScores(PortfolioAiAnalysisResult r, List<HoldingTry> rows, BigDecimal total,
                               Concentration conc, RiskMetrics m) {
        boolean hasMetrics = m != null && m.available();
        double topHolding = conc.topHoldingPercent() != null ? conc.topHoldingPercent().doubleValue() : 0;
        double hhi = conc.herfindahl() != null ? conc.herfindahl().doubleValue() : 0;

        double concComp = clamp(0.6 * topHolding + 0.4 * hhi * 100, 0, 100);
        double assetComp = assetTypeRiskComponent(rows, total);
        double volComp = hasMetrics && m.annualVolatilityPercent() != null
                ? clamp(m.annualVolatilityPercent().doubleValue() / 40.0 * 100, 0, 100) : 0;
        double ddComp = hasMetrics && m.maxDrawdownPercent() != null
                ? clamp(m.maxDrawdownPercent().doubleValue() / 50.0 * 100, 0, 100) : 0;

        // RİSK SKORU (yüksek = riskli)
        List<ScoreFactor> riskFactors = new ArrayList<>();
        double risk;
        if (hasMetrics) {
            risk = 0.28 * volComp + 0.24 * concComp + 0.26 * assetComp + 0.22 * ddComp;
            riskFactors.add(factor("Volatilite", 0.28 * volComp, "Yıllık dalgalanma katkısı"));
            riskFactors.add(factor("Yoğunlaşma", 0.24 * concComp, "En büyük pozisyon + Herfindahl"));
            riskFactors.add(factor("Varlık tipi", 0.26 * assetComp, "Kripto/vadeli yüksek, tahvil/mevduat düşük"));
            riskFactors.add(factor("Max düşüş", 0.22 * ddComp, "Tepe-dip kayıp katkısı"));
        } else {
            risk = 0.55 * concComp + 0.45 * assetComp;
            riskFactors.add(factor("Yoğunlaşma", 0.55 * concComp, "En büyük pozisyon + Herfindahl"));
            riskFactors.add(factor("Varlık tipi", 0.45 * assetComp, "Kripto/vadeli yüksek, tahvil/mevduat düşük"));
            riskFactors.add(factor("Geçmiş kısa", 0, "Volatilite/drawdown hesaplanamadı"));
        }
        r.setRiskScore((int) Math.round(clamp(risk, 0, 100)));
        r.setRiskLabel(risk >= 66 ? "Yüksek" : risk >= 33 ? "Orta" : "Düşük");
        r.setRiskFactors(riskFactors);

        // SAĞLIK SKORU (yüksek = sağlıklı)
        double diversComp = 100 - concComp;
        double real = r.getRealProfitLossPercent() != null ? r.getRealProfitLossPercent().doubleValue() : 0;
        double realComp = clamp(50 + real * 2.5, 0, 100);
        double sharpeComp = hasMetrics && m.sharpe() != null ? clamp(m.sharpe().doubleValue() / 2.0 * 100, 0, 100) : 0;
        double ddCtrlComp = 100 - ddComp;

        List<ScoreFactor> healthFactors = new ArrayList<>();
        double health;
        if (hasMetrics) {
            health = 0.28 * diversComp + 0.24 * sharpeComp + 0.26 * realComp + 0.22 * ddCtrlComp;
            healthFactors.add(factor("Çeşitlendirme", 0.28 * diversComp, "Yoğunlaşma ne kadar düşükse o kadar iyi"));
            healthFactors.add(factor("Risk-ayarlı getiri", 0.24 * sharpeComp, "Sharpe oranı"));
            healthFactors.add(factor("Reel getiri", 0.26 * realComp, "Enflasyondan arındırılmış getiri"));
            healthFactors.add(factor("Düşüş kontrolü", 0.22 * ddCtrlComp, "Max drawdown ne kadar küçükse o kadar iyi"));
        } else {
            health = 0.55 * diversComp + 0.45 * realComp;
            healthFactors.add(factor("Çeşitlendirme", 0.55 * diversComp, "Yoğunlaşma ne kadar düşükse o kadar iyi"));
            healthFactors.add(factor("Reel getiri", 0.45 * realComp, "Enflasyondan arındırılmış getiri"));
        }
        r.setHealthScore((int) Math.round(clamp(health, 0, 100)));
        r.setHealthLabel(health >= 66 ? "Sağlıklı" : health >= 33 ? "Orta" : "Zayıf");
        r.setHealthFactors(healthFactors);
    }

    /** Pozisyonları (tip/sembol/ağırlık) tarihsel risk servisine geçmek için hazırlar. */
    private static List<PortfolioHistoricalRiskService.Position> buildPositions(List<HoldingTry> rows, BigDecimal total) {
        List<PortfolioHistoricalRiskService.Position> out = new ArrayList<>();
        double t = total.doubleValue();
        if (t <= 0) {
            return out;
        }
        for (HoldingTry row : rows) {
            PortfolioHoldingResponse h = row.h();
            if (h.getSymbol() == null || h.getSymbol().isBlank() || h.getAssetType() == null) {
                continue;
            }
            out.add(new PortfolioHistoricalRiskService.Position(
                    h.getAssetType(), h.getSymbol(), row.mvTry().doubleValue() / t));
        }
        return out;
    }

    // ── Çok-ufuklu tahmin (Monte Carlo aralık + varlık-bazlı medyan getiri) ─────

    private static final int[] FORECAST_MONTHS = {1, 3, 12};
    private static final String[] FORECAST_LABELS = {"1 Ay", "3 Ay", "1 Yıl"};
    private static final int FORECAST_TOP_ASSETS = 6;

    private Forecast buildForecast(List<HoldingTry> rows, BigDecimal totalValue, RiskMetrics metrics,
                                   List<AssetSignal> signals) {
        if (metrics == null || !metrics.available() || totalValue == null || totalValue.signum() <= 0
                || metrics.annualReturnPercent() == null || metrics.annualVolatilityPercent() == null) {
            return new Forecast(List.of(), List.of(), false,
                    "Risk metrikleri için geçmiş yetersiz; sayısal tahmin üretilemedi.");
        }
        // Portföy ufukları — mevcut MC motoru her ufukta (kapalı-form lognormal).
        List<ForecastHorizon> ph = new ArrayList<>();
        for (int i = 0; i < FORECAST_MONTHS.length; i++) {
            var mc = monteCarloService.project(totalValue, metrics.annualReturnPercent(),
                    metrics.annualVolatilityPercent(), FORECAST_MONTHS[i]);
            if (mc.available()) {
                ph.add(new ForecastHorizon(FORECAST_LABELS[i], FORECAST_MONTHS[i], mc.medianEndValue(),
                        mc.p5EndValue(), mc.p95EndValue(), mc.expectedReturnPercent(), mc.probLossPercent()));
            }
        }

        Map<String, String> trendBySymbol = new LinkedHashMap<>();
        if (signals != null) {
            for (AssetSignal s : signals) {
                trendBySymbol.put(s.symbol(), s.trend());
            }
        }

        // Varlık tahminleri — ağırlıkça en büyük FORECAST_TOP_ASSETS pozisyon (maliyeti sınırla).
        List<HoldingTry> top = new ArrayList<>(rows);
        top.sort(Comparator.comparing(HoldingTry::mvTry, Comparator.reverseOrder()));
        List<AssetForecast> af = new ArrayList<>();
        int n = 0;
        for (HoldingTry row : top) {
            if (n >= FORECAST_TOP_ASSETS) {
                break;
            }
            PortfolioHoldingResponse h = row.h();
            if (h.getSymbol() == null || h.getSymbol().isBlank() || h.getAssetType() == null) {
                continue;
            }
            n++;
            String label = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();
            String trend = trendBySymbol.getOrDefault(h.getSymbol(), "NEUTRAL");
            RiskMetrics am = historicalRiskService.computeFromHoldings(
                    List.of(new PortfolioHistoricalRiskService.Position(h.getAssetType(), h.getSymbol(), 1.0)), 12);
            af.add(new AssetForecast(h.getSymbol(), label, pctOf(row.mvTry(), totalValue), trend,
                    assetMedianReturn(am, 1), assetMedianReturn(am, 3), assetMedianReturn(am, 12)));
        }

        return new Forecast(ph, af, !ph.isEmpty(),
                "Geçmiş getiri/volatilitenin süreceği varsayımıyla olası senaryo; kesin tahmin değildir.");
    }

    /** Tek varlığın bir ufuktaki MEDYAN getirisi (%) — MC motorunun expectedReturn'ü (lognormal medyan). */
    private BigDecimal assetMedianReturn(RiskMetrics am, int months) {
        if (am == null || !am.available() || am.annualReturnPercent() == null
                || am.annualVolatilityPercent() == null) {
            return null;
        }
        var mc = monteCarloService.project(BigDecimal.valueOf(100), am.annualReturnPercent(),
                am.annualVolatilityPercent(), months);
        return mc.available() ? mc.expectedReturnPercent() : null;
    }

    /** Hesaplanmış tip-dağılımından (tip enum adı → %) — rebalancing girdisi. */
    private static Map<String, BigDecimal> currentTypePercents(PortfolioAiAnalysisResult r) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (r.getAssetTypeAllocation() != null) {
            for (AllocationSlice s : r.getAssetTypeAllocation()) {
                out.merge(s.assetType(), s.weightPercent(), BigDecimal::add);
            }
        }
        return out;
    }

    /** Varlık tipi (enum adı) → portföy ağırlığı (kesir 0-1) — stres testi proxy'leri için. */
    private static Map<String, Double> typeWeightFractions(List<HoldingTry> rows, BigDecimal total) {
        Map<String, Double> out = new LinkedHashMap<>();
        double t = total.doubleValue();
        if (t <= 0) {
            return out;
        }
        for (HoldingTry row : rows) {
            out.merge(typeName(row.h().getAssetType()), row.mvTry().doubleValue() / t, Double::sum);
        }
        return out;
    }

    private double assetTypeRiskComponent(List<HoldingTry> rows, BigDecimal total) {
        double sum = 0;
        for (HoldingTry row : rows) {
            double w = row.mvTry().doubleValue() / total.doubleValue();
            sum += w * assetTypeRisk(row.h().getAssetType());
        }
        return clamp(sum, 0, 100);
    }

    /** Varlık tipi risk ağırlığı (0-100). Bilinmeyen tip → 50 (orta). */
    private static double assetTypeRisk(AssetType type) {
        if (type == null) {
            return 50;
        }
        return switch (type.name()) {
            case "FUTURE" -> 100;
            case "CRYPTO" -> 95;
            case "STOCK" -> 60;
            case "COMMODITY" -> 55;
            case "FUND" -> 45;
            case "FX" -> 40;
            case "SILVER" -> 45;
            case "GOLD" -> 35;
            case "BOND" -> 20;
            default -> 50;
        };
    }

    // ── İstatistik yardımcıları ─────────────────────────────────────────────────

    /** Seri[i]/cost[i] oranları (geçerli noktalar). */
    private static List<Double> ratioSeries(List<WhatIfSeriesPoint> pts, Function<WhatIfSeriesPoint, BigDecimal> field) {
        List<Double> out = new ArrayList<>();
        for (WhatIfSeriesPoint p : pts) {
            BigDecimal v = field.apply(p);
            BigDecimal c = p.getCost();
            if (v != null && v.signum() > 0 && c != null && c.signum() > 0) {
                out.add(v.doubleValue() / c.doubleValue());
            }
        }
        return out;
    }

    /** Ardışık oranlardan dönemsel getiriler: r_i = ratio[i]/ratio[i-1] − 1. */
    private static List<Double> returns(List<Double> ratio) {
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < ratio.size(); i++) {
            double prev = ratio.get(i - 1);
            if (prev > 1e-9) {
                out.add(ratio.get(i) / prev - 1);
            }
        }
        return out;
    }

    private static double mean(List<Double> xs) {
        if (xs.isEmpty()) return 0;
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.size();
    }

    private static double variance(List<Double> xs) {
        if (xs.size() < 2) return 0;
        double m = mean(xs);
        double s = 0;
        for (double x : xs) s += (x - m) * (x - m);
        return s / (xs.size() - 1);
    }

    private static double stddev(List<Double> xs) {
        return Math.sqrt(variance(xs));
    }

    private static double downsideDev(List<Double> xs) {
        if (xs.isEmpty()) return 0;
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
        if (n < 2) return 0;
        double ma = mean(a.subList(0, n));
        double mb = mean(b.subList(0, n));
        double s = 0;
        for (int i = 0; i < n; i++) s += (a.get(i) - ma) * (b.get(i) - mb);
        return s / (n - 1);
    }

    /** Max drawdown (kümülatif oran serisi üzerinde tepe-dip, 0-1). */
    private static double maxDrawdown(List<Double> ratio) {
        double peak = Double.NEGATIVE_INFINITY;
        double maxDd = 0;
        for (double v : ratio) {
            if (v > peak) peak = v;
            if (peak > 1e-9) {
                double dd = (peak - v) / peak;
                if (dd > maxDd) maxDd = dd;
            }
        }
        return maxDd;
    }

    // ── Küçük yardımcılar ───────────────────────────────────────────────────────

    private WhatIfSeriesResult safeSeries(PortfolioResponse resp) {
        try {
            return whatIfService.computeSeries(resp, null, null, List.of());
        } catch (Exception e) {
            log.warn("AI analiz değer serisi alınamadı: {}", e.getMessage());
            return null;
        }
    }

    private static BigDecimal profitPercent(PortfolioResponse resp) {
        if (resp == null || resp.getTotalCost() == null || resp.getTotalCost().signum() <= 0
                || resp.getTotalMarketValue() == null) {
            return null;
        }
        return pct(resp.getTotalMarketValue(), resp.getTotalCost());
    }

    private static ScoreFactor factor(String label, double contribution, String detail) {
        return new ScoreFactor(label, (int) Math.round(contribution), detail);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal base) {
        return value.divide(base, java.math.MathContext.DECIMAL64)
                .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctOf(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) return BigDecimal.ZERO;
        return part.multiply(HUNDRED).divide(total, PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Oran (0-1) → yüzde. */
    private static BigDecimal pctVal(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal num(double v) {
        return BigDecimal.valueOf(v).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scalePct(BigDecimal v) {
        return v == null ? null : v.setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static String typeName(AssetType t) {
        return t != null ? t.name() : "OTHER";
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

    /** Holding + TL'ye çevrilmiş değer/maliyet/kâr taşıyıcısı. */
    private record HoldingTry(PortfolioHoldingResponse h, BigDecimal mvTry, BigDecimal costTry, BigDecimal plTry) {}
}
