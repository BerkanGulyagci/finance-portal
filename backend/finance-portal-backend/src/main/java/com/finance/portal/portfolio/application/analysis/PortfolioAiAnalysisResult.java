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

    // ── Değer zaman serisi (grafik) ─────────────────────────────────────────────
    private List<SeriesPoint> valueSeries;

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

    /** Risk-ayarlı metrikler. available=false ise geçmiş çok kısa (hesaplanamadı). */
    public record RiskMetrics(BigDecimal annualVolatilityPercent, BigDecimal sharpe, BigDecimal sortino,
                              BigDecimal maxDrawdownPercent, BigDecimal beta, int sampleMonths,
                              boolean available, String note) {}

    /** Benchmark: aynı parayı o araca koysaydın getirisi % + portföy farkı (pozitif = portföy önde). */
    public record BenchmarkItem(String key, String label, BigDecimal returnPercent, BigDecimal deltaVsPortfolio) {}

    /** Değer serisi noktası: tarih + gerçek değer (TL) + o ana kadarki maliyet (TL). */
    public record SeriesPoint(LocalDate date, BigDecimal value, BigDecimal cost) {}
}
