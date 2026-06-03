package com.finance.portal.portfolio.service;

import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AllocationSlice;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetForecast;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetReturn;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetSignal;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.BenchmarkItem;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Classification;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Concentration;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Forecast;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.ForecastHorizon;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Rebalance;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RebalanceItem;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.StressTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ek testler: var olan {@link PortfolioAiNarratorTest} cache odaklıdır. Burada
 * {@code buildMetricsPrompt}'ın DAL'larını (concentration/riskMetrics available↔unavailable,
 * allocation, gainer/loser, benchmark ÖNDE/GERİDE/karşılaştırılamadı, stres available↔skip,
 * classification, assetSignals >8 + getiri null/dolu, forecast + assetForecasts, rebalance
 * skip↔include) hedefliyoruz. AssistantChatPort mock'lanır; üretilen prompt yakalanıp doğrulanır.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioAiNarratorMoreTest {

    @Mock AssistantChatPort chatPort;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private PortfolioAiNarrator narrator;

    @BeforeEach
    void setUp() {
        narrator = new PortfolioAiNarrator(chatPort, redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        // Cache miss + LLM yanıtı → her testte buildMetricsPrompt çalışır.
        lenient().when(valueOps.get(anyString())).thenReturn(null);
        lenient().when(chatPort.complete(any(), any())).thenReturn("rapor");
    }

    private static PortfolioAiAnalysisResult base() {
        PortfolioAiAnalysisResult r = new PortfolioAiAnalysisResult();
        r.setPortfolioId(UUID.randomUUID());
        r.setTotalValueTry(new BigDecimal("12345.67"));
        r.setTotalCostTry(new BigDecimal("10000"));
        r.setTotalProfitLossPercent(new BigDecimal("23.45"));
        r.setRealProfitLossPercent(new BigDecimal("3.45"));
        r.setInflationSincePercent(new BigDecimal("20.00"));
        r.setRiskScore(60);
        r.setRiskLabel("Orta");
        r.setHealthScore(70);
        r.setHealthLabel("İyi");
        r.setHoldingsCount(5);
        return r;
    }

    /** generate'i çağırıp LLM'e giden kullanıcı prompt'unu (2. mesaj) yakalar. */
    private String capturePrompt(PortfolioAiAnalysisResult r) {
        String out = narrator.generate(r, "u1", "User", "u@x.com");
        assertThat(out).isEqualTo("rapor");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = ArgumentCaptor.forClass(List.class);
        verify(chatPort).complete(cap.capture(), any());
        List<ChatMessage> msgs = cap.getValue();
        assertThat(msgs).hasSize(2);
        // Kullanıcı mesajı (metrik prompt'u) ikinci sırada.
        return msgs.get(1).content();
    }

    @Test
    void prompt_allSectionsPopulated_hitsTrueArms() {
        PortfolioAiAnalysisResult r = base();

        r.setConcentration(new Concentration(
                new BigDecimal("60.0"), new BigDecimal("80.0"),
                new BigDecimal("0.4"), "Yüksek", "AAPL"));

        // available=true → risk metrikleri true-arm
        r.setRiskMetrics(new RiskMetrics(
                new BigDecimal("18.0"), new BigDecimal("22.5"),
                new BigDecimal("1.2"), new BigDecimal("1.5"),
                new BigDecimal("-15.0"), new BigDecimal("0.9"),
                24, true, null));

        r.setAssetTypeAllocation(List.of(
                new AllocationSlice("Hisse", "STOCK", new BigDecimal("7000"), new BigDecimal("70.0")),
                new AllocationSlice("Altın", "GOLD", new BigDecimal("3000"), new BigDecimal("30.0"))));

        r.setTopGainers(List.of(new AssetReturn("AAPL", "Apple", "STOCK",
                new BigDecimal("40.0"), new BigDecimal("55.0"), new BigDecimal("500"))));
        r.setTopLosers(List.of(new AssetReturn("XYZ", "Xyz", "STOCK",
                new BigDecimal("10.0"), new BigDecimal("-12.0"), new BigDecimal("-100"))));

        // Benchmark: ÖNDE (delta>=0, mag!=null)
        r.setBenchmarks(List.of(new BenchmarkItem("BIST100", "BIST 100",
                new BigDecimal("10.0"), new BigDecimal("5.0"))));

        // Stres: biri available=true (yazılır), biri available=false (atlanır)
        r.setStressTests(List.of(
                new StressTest("2020", "Covid", "2020-Q1", new BigDecimal("-30.0"), null, true),
                new StressTest("2008", "Lehman", "2008", null, "veri yok", false)));

        r.setClassification(new Classification("AGGRESSIVE", "Agresif", "Büyüme odaklı.",
                new BigDecimal("70.0"), new BigDecimal("30.0")));

        // assetSignals: getiri DOLU
        r.setAssetSignals(List.of(new AssetSignal("AAPL", "Apple", "STOCK",
                new BigDecimal("40.0"), "UP", "Yükseliş", "MA20 üstünde",
                new BigDecimal("85.0"), new BigDecimal("5.0"), new BigDecimal("8.0"),
                new BigDecimal("55.0"), "Güçlü trend")));

        // forecast available=true + horizons + assetForecasts dolu
        r.setForecast(new Forecast(
                List.of(new ForecastHorizon("1 ay", 1, new BigDecimal("13000"),
                        new BigDecimal("11000"), new BigDecimal("15000"),
                        new BigDecimal("5.0"), new BigDecimal("20.0"))),
                List.of(new AssetForecast("AAPL", "Apple", new BigDecimal("40.0"), "UP",
                        new BigDecimal("2.0"), new BigDecimal("5.0"), new BigDecimal("12.0"))),
                true, null));

        // rebalance: bir item |delta|>=3 (yazılır), bir item |delta|<3 (atlanır)
        r.setRebalance(new Rebalance("BALANCED", "Dengeli",
                List.of(
                        new RebalanceItem("STOCK", "Hisse", new BigDecimal("70.0"),
                                new BigDecimal("60.0"), new BigDecimal("-10.0"), "AZALT"),
                        new RebalanceItem("GOLD", "Altın", new BigDecimal("30.0"),
                                new BigDecimal("31.0"), new BigDecimal("1.0"), "KORU")),
                new BigDecimal("12.0"), null));

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("Yoğunlaşma");
        assertThat(prompt).contains("AAPL");
        assertThat(prompt).contains("yıllık volatilite");
        assertThat(prompt).contains("Sharpe");
        assertThat(prompt).contains("Varlık tipi dağılımı");
        assertThat(prompt).contains("En çok kazandıran");
        assertThat(prompt).contains("En çok kaybettiren");
        assertThat(prompt).contains("portföy ÖNDE");
        assertThat(prompt).contains("(fark %5.0)");
        assertThat(prompt).contains("Covid");
        // available=false stres testi yazılmaz:
        assertThat(prompt).doesNotContain("Lehman");
        assertThat(prompt).contains("Portföy profili");
        assertThat(prompt).contains("Agresif");
        assertThat(prompt).contains("teknik sinyalleri");
        assertThat(prompt).contains("getiri %55.0");
        assertThat(prompt).contains("Çok-ufuklu tahmin");
        assertThat(prompt).contains("Varlık medyan getiri tahmini");
        assertThat(prompt).contains("Yeniden dengeleme");
        assertThat(prompt).contains("azalt"); // action lowerCase
        // |delta|<3 olan KORU item'ı atlanır → "koru" eylem cümlesi yazılmaz:
        assertThat(prompt).doesNotContain("koru (");
    }

    @Test
    void prompt_riskMetricsUnavailable_writesShortHistoryNote() {
        PortfolioAiAnalysisResult r = base();
        r.setRiskMetrics(new RiskMetrics(null, null, null, null, null, null, 2, false, "kısa"));

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("geçmiş çok kısa, hesaplanamadı");
        assertThat(prompt).doesNotContain("yıllık volatilite");
    }

    @Test
    void prompt_benchmarkBehindAndNullDelta() {
        PortfolioAiAnalysisResult r = base();
        // delta < 0 → GERİDE (mag != null), ayrıca delta == null → karşılaştırılamadı (mag == null)
        List<BenchmarkItem> benches = new ArrayList<>();
        benches.add(new BenchmarkItem("GOLD", "Altın", new BigDecimal("30.0"), new BigDecimal("-8.0")));
        benches.add(new BenchmarkItem("USD", "Dolar", new BigDecimal("15.0"), null));
        r.setBenchmarks(benches);

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("portföy GERİDE");
        assertThat(prompt).contains("(fark %8.0)");
        assertThat(prompt).contains("karşılaştırılamadı");
        // null delta için fark parantezi YOK
        assertThat(prompt).contains("Dolar: %15.0 → karşılaştırılamadı");
    }

    @Test
    void prompt_assetSignals_capsAt8_andNullProfit() {
        PortfolioAiAnalysisResult r = base();
        List<AssetSignal> sigs = new ArrayList<>();
        // 10 sinyal → n++ >= 8 break dalı (9. ve 10. yazılmaz). İlk sinyalin getirisi null.
        for (int i = 0; i < 10; i++) {
            BigDecimal pl = (i == 0) ? null : new BigDecimal("3.0");
            sigs.add(new AssetSignal("S" + i, "Sig" + i, "STOCK",
                    new BigDecimal("5.0"), "NEUTRAL", "Nötr", "MA civarı",
                    new BigDecimal("50.0"), new BigDecimal("0.0"), new BigDecimal("0.0"),
                    pl, "not" + i));
        }
        r.setAssetSignals(sigs);

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("Sig0"); // getiri null → "— getiri %" eklenmez
        assertThat(prompt).contains("not0");
        assertThat(prompt).doesNotContain("Sig8"); // 9. eleman (index 8) cap'i aşar
        assertThat(prompt).doesNotContain("Sig9");
        // ilk satırda getiri yazılmaz (null), ama dolu olanlar yazılır
        assertThat(prompt).contains("getiri %3.0");
    }

    @Test
    void prompt_forecastAvailableButNoAssetForecasts() {
        PortfolioAiAnalysisResult r = base();
        // available=true, horizons dolu ama assetForecasts BOŞ → iç if false-arm
        r.setForecast(new Forecast(
                List.of(new ForecastHorizon("3 ay", 3, new BigDecimal("14000"),
                        new BigDecimal("12000"), new BigDecimal("16000"),
                        new BigDecimal("10.0"), new BigDecimal("18.0"))),
                List.of(), true, null));

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("Çok-ufuklu tahmin");
        assertThat(prompt).contains("3 ay");
        assertThat(prompt).doesNotContain("Varlık medyan getiri tahmini");
    }

    @Test
    void prompt_forecastUnavailable_skipsForecastSection() {
        PortfolioAiAnalysisResult r = base();
        // available=false → forecast bloğu hiç yazılmaz (kısa-devre dalı)
        r.setForecast(new Forecast(List.of(), List.of(), false, "yetersiz"));

        String prompt = capturePrompt(r);

        assertThat(prompt).doesNotContain("Çok-ufuklu tahmin");
    }

    @Test
    void prompt_rebalanceAllItemsBelowThreshold_writesHeaderNoAdjustments() {
        PortfolioAiAnalysisResult r = base();
        // Tüm item'lar |delta|<3 → hepsi atlanır ama başlık + drift yazılır.
        r.setRebalance(new Rebalance("CONSERVATIVE", "Korumacı",
                List.of(new RebalanceItem("BOND", "Tahvil", new BigDecimal("40.0"),
                        new BigDecimal("41.0"), new BigDecimal("1.0"), "KORU")),
                new BigDecimal("2.0"), null));

        String prompt = capturePrompt(r);

        assertThat(prompt).contains("Yeniden dengeleme");
        assertThat(prompt).contains("Korumacı");
        // hiçbir item yazılmadı
        assertThat(prompt).doesNotContain("→%");
    }

    @Test
    void prompt_emptyCollections_skipOptionalSections() {
        PortfolioAiAnalysisResult r = base();
        // Boş listeler → her optional bölümün !isEmpty() false-arm'ı.
        r.setAssetTypeAllocation(List.of());
        r.setTopGainers(List.of());
        r.setTopLosers(List.of());
        r.setBenchmarks(List.of());
        r.setStressTests(List.of());
        r.setAssetSignals(List.of());

        String prompt = capturePrompt(r);

        assertThat(prompt).doesNotContain("Varlık tipi dağılımı");
        assertThat(prompt).doesNotContain("En çok kazandıran");
        assertThat(prompt).doesNotContain("Benchmark karşılaştırması");
        assertThat(prompt).doesNotContain("stres testleri");
        assertThat(prompt).doesNotContain("teknik sinyalleri");
        // Zorunlu bölümler hâlâ var:
        assertThat(prompt).contains("Risk skoru");
    }
}
