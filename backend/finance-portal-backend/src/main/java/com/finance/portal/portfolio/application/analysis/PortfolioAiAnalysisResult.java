package com.finance.portal.portfolio.application.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AI Portföy Analizi sonucu — TÜM sayılar BACKEND'de deterministik hesaplanır
 * ({@code PortfolioAnalysisService}); {@link #aiReport} ise bu hesaplanmış metrikleri
 * doğal dile çeviren LLM yorumudur (sayı üretmez, yalnız yorumlar). Frontend grafikleri
 * bu sayılardan çizer — AI metninden DEĞİL.
 *
 * <p>Skorlar şeffaf formülle üretilir; {@code *Factors} listeleri her bileşenin skora
 * katkısını gösterir ("neden 72?" sorusunun cevabı).
 */
@Getter
@Setter
@NoArgsConstructor
public class PortfolioAiAnalysisResult {

    private UUID portfolioId;
    private String name;
    private LocalDate asOf;

    // ── Özet ───────────────────────────────────────────────────────────────────
    private BigDecimal totalValueTry;
    private BigDecimal totalCostTry;
    private BigDecimal totalProfitLossTry;
    /** Nominal toplam getiri %. */
    private BigDecimal totalProfitLossPercent;
    /** Reel (TÜFE'den arındırılmış) toplam getiri %. */
    private BigDecimal realProfitLossPercent;
    /** İlk alıştan bugüne birikimli TÜFE enflasyonu %. */
    private BigDecimal inflationSincePercent;
    private int holdingsCount;

    // ── Skorlar (şeffaf formül; yüksek risk = riskli, yüksek sağlık = iyi) ──────
    private int riskScore;
    private String riskLabel;
    private List<ScoreFactor> riskFactors;
    private int healthScore;
    private String healthLabel;
    private List<ScoreFactor> healthFactors;

    // ── Dağılım (pasta) ─────────────────────────────────────────────────────────
    private List<AllocationSlice> allocation;
    private List<AllocationSlice> assetTypeAllocation;

    // ── Varlık bazlı getiri ─────────────────────────────────────────────────────
    private List<AssetReturn> assetReturns;
    private List<AssetReturn> topGainers;
    private List<AssetReturn> topLosers;

    // ── Yoğunlaşma riski ────────────────────────────────────────────────────────
    private Concentration concentration;

    // ── Risk-ayarlı metrikler (değer/maliyet oran serisinden) ───────────────────
    private RiskMetrics riskMetrics;

    // ── Benchmark karşılaştırması ───────────────────────────────────────────────
    private List<BenchmarkItem> benchmarks;

    // ── Tarihsel stres testleri (2008/2018/2020 krizleri) ───────────────────────
    private List<StressTest> stressTests;

    // ── Değer zaman serisi (grafik) ─────────────────────────────────────────────
    private List<SeriesPoint> valueSeries;

    // ── Portföy kimliği / sınıflandırma (agresif / dengeli / korumacı) ──────────
    private Classification classification;

    // ── Varlık-bazlı teknik sinyaller (MA20/50, 52h konumu, momentum) ───────────
    private List<AssetSignal> assetSignals;

    // ── Monte Carlo / lognormal projeksiyon (olası değer aralığı) ───────────────
    private MonteCarlo monteCarlo;

    // ── Yeniden dengeleme (risk profiline göre hedef dağılım vs mevcut) ─────────
    private Rebalance rebalance;

    // ── Çok-ufuklu tahmin (1ay/3ay/1y): kod aralık üretir, AI yön/anlatı yorumlar ─
    private Forecast forecast;

    // ── AI yorum raporu ─────────────────────────────────────────────────────────
    /** LLM'in ürettiği Türkçe yorum; LLM kullanılamazsa null (graceful degrade). */
    private String aiReport;
    private boolean aiReportAvailable;

    /** Disclaimer + eksik-veri uyarıları. */
    private List<String> notes;

    // ── Nested ──────────────────────────────────────────────────────────────────

    /** Bir skorun bir bileşeninin katkısı (şeffaflık: "neden bu skor?"). */
    public record ScoreFactor(String label, int contribution, String detail) {}

    /** Pasta dilimi: etiket + TL değer + ağırlık %. */
    public record AllocationSlice(String label, String assetType, BigDecimal valueTry, BigDecimal weightPercent) {}

    /** Varlık satırı: getiri % + TL K/Z + portföy ağırlığı. */
    public record AssetReturn(String symbol, String name, String assetType,
                              BigDecimal weightPercent, BigDecimal profitLossPercent, BigDecimal profitLossTry) {}

    /** Yoğunlaşma: en büyük pozisyon %, top-3 %, Herfindahl (0-1), etiket. */
    public record Concentration(BigDecimal topHoldingPercent, BigDecimal top3Percent,
                                BigDecimal herfindahl, String label, String topHoldingLabel) {}

    /** Risk-ayarlı metrikler. available=false ise geçmiş çok kısa (hesaplanamadı).
     *  {@code annualReturnPercent} = yıllıklandırılmış getiri (Monte Carlo μ kaynağı). */
    public record RiskMetrics(BigDecimal annualReturnPercent, BigDecimal annualVolatilityPercent,
                              BigDecimal sharpe, BigDecimal sortino,
                              BigDecimal maxDrawdownPercent, BigDecimal beta, int sampleMonths,
                              boolean available, String note) {}

    /** Benchmark: aynı parayı o araca koysaydın getirisi % + portföy farkı (pozitif = portföy önde). */
    public record BenchmarkItem(String key, String label, BigDecimal returnPercent, BigDecimal deltaVsPortfolio) {}

    /** Değer serisi noktası: tarih + gerçek değer (TL) + o ana kadarki maliyet (TL). */
    public record SeriesPoint(LocalDate date, BigDecimal value, BigDecimal cost) {}

    /** Tarihsel stres testi: mevcut dağılımın o krizdeki tahmini etkisi (%). available=false ise temsil edilemedi. */
    public record StressTest(String key, String label, String period, BigDecimal impactPercent,
                             String note, boolean available) {}

    /**
     * Portföy kimliği / risk profili — varlık-tipi ağırlıklarından türetilir.
     * profile: AGGRESSIVE | BALANCED | CONSERVATIVE. growthWeight = büyüme-odaklı (hisse/kripto/vadeli/emtia),
     * defensiveWeight = korumacı (tahvil/altın/döviz/mevduat) ağırlığı %.
     */
    public record Classification(String profile, String label, String detail,
                                 BigDecimal growthWeightPercent, BigDecimal defensiveWeightPercent) {}

    /**
     * Tek varlığın teknik sinyali (holdings'te zaten enrich edilmiş alanlardan — ekstra dış çağrı yok).
     * trend: UP | DOWN | NEUTRAL. maState ör. "Fiyat MA20 ve MA50 üstünde". range52wPercent: 52-hafta
     * bandındaki konum (0=dip,100=tepe). momentum1m/3m: 1/3 aylık % değişim. note: kısa Türkçe açıklama.
     */
    public record AssetSignal(String symbol, String name, String assetType, BigDecimal weightPercent,
                              String trend, String trendLabel, String maState, BigDecimal range52wPercent,
                              BigDecimal momentum1mPercent, BigDecimal momentum3mPercent,
                              BigDecimal profitLossPercent, String note) {}

    /**
     * Monte Carlo / lognormal projeksiyon: GBM (μ=yıllık getiri, σ=yıllık volatilite) altında portföyün
     * gelecek değer aralığı. available=false ise yeterli geçmiş yok. bands = ay-ay yüzdelik bantlar.
     */
    public record MonteCarlo(int horizonMonths, boolean available, String note,
                             List<ProjectionPoint> bands,
                             BigDecimal startValue, BigDecimal medianEndValue,
                             BigDecimal p5EndValue, BigDecimal p95EndValue,
                             BigDecimal expectedReturnPercent, BigDecimal probLossPercent,
                             BigDecimal annualReturnPercent, BigDecimal annualVolatilityPercent) {}

    /** Projeksiyon noktası: ay + yüzdelik dilim değerleri (p5/p25/p50/p75/p95, TL). */
    public record ProjectionPoint(int month, BigDecimal p5, BigDecimal p25, BigDecimal p50,
                                  BigDecimal p75, BigDecimal p95) {}

    /**
     * Yeniden dengeleme önerisi (EĞİTSEL, tavsiye değil): seçilen risk profiline ait örnek hedef tip-dağılımı
     * ile mevcut dağılımın karşılaştırması. basedOnProfile: CONSERVATIVE | BALANCED | AGGRESSIVE.
     * driftPercent = mutlak sapmaların toplamı / 2 (0=birebir uyum, 100=tamamen farklı).
     */
    public record Rebalance(String basedOnProfile, String profileLabel, List<RebalanceItem> items,
                            BigDecimal driftPercent, String note) {}

    /** Tek tip için mevcut vs hedef ağırlık ve önerilen yön. action: ARTIR | AZALT | KORU. */
    public record RebalanceItem(String assetType, String label, BigDecimal currentPercent,
                                BigDecimal targetPercent, BigDecimal deltaPercent, String action) {}

    /**
     * Çok-ufuklu tahmin: SAYISAL aralık (Monte Carlo/lognormal) KOD'da üretilir; AI bu aralıkları + teknik
     * sinyalleri yorumlayıp YÖN/anlatı tahmini yapar (aiReport "Gelecek Görünüm" bölümü). available=false ise
     * geçmiş yetersiz. portfolioHorizons = portföy 1ay/3ay/1y; assetForecasts = öne çıkan varlıklar.
     */
    public record Forecast(List<ForecastHorizon> portfolioHorizons, List<AssetForecast> assetForecasts,
                           boolean available, String note) {}

    /** Portföyün bir ufuktaki olası değer aralığı (TL) + medyan getiri & kayıp olasılığı. */
    public record ForecastHorizon(String label, int months, BigDecimal median, BigDecimal p5, BigDecimal p95,
                                  BigDecimal expectedReturnPercent, BigDecimal probLossPercent) {}

    /** Tek varlığın ufuk-bazlı MEDYAN getiri tahmini (%) + trend yönü. null = geçmiş yetersiz. */
    public record AssetForecast(String symbol, String name, BigDecimal weightPercent, String trend,
                                BigDecimal return1mPercent, BigDecimal return3mPercent, BigDecimal return12mPercent) {}
}
