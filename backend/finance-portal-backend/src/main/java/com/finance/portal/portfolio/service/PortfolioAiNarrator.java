package com.finance.portal.portfolio.service;

import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.assistant.application.tools.ToolContext;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AllocationSlice;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetReturn;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetSignal;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.BenchmarkItem;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.MonteCarlo;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.StressTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * AI analiz YORUMCUSU — {@link PortfolioAnalysisService}'in DETERMİNİSTİK hesapladığı metrikleri
 * doğal dile çevirir. Yeni sayı ÜRETMEZ, yalnız verilen sayıları yorumlar; "al/sat" tavsiyesi vermez.
 *
 * <p>Çağrı, hesaplanmış (toplu/anonim) metrikleri sistem+kullanıcı mesajı olarak {@link AssistantChatPort}
 * zincirine (Groq→Gemini) geçirir. Sonuç metrik-hash anahtarıyla Redis'te cache'lenir → aynı portföy
 * tekrar açıldığında LLM tekrar çağrılmaz (kota/maliyet). LLM çökerse çağıran graceful degrade eder.</p>
 */
@Component
public class PortfolioAiNarrator {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAiNarrator.class);
    private static final String CACHE_PREFIX = "ai-analysis:narrative:";
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private static final String SYSTEM_PROMPT = """
            Sen Portiva uygulamasının kıdemli bir finans analistisin — bir "portföy motoru" gibi davran.
            Sana KULLANICININ portföyü için ZATEN HESAPLANMIŞ metrikler verilecek. Görevin bu metrikleri
            Türkçe, anlaşılır, DERİN ve dengeli bir raporla yorumlamak; portföyün NE TÜR bir portföy olduğunu
            ve tek tek varlıkların ne durumda olduğunu açıklamak.

            KURALLAR:
            - ASLA yeni sayı/oran UYDURMA; sadece sana verilen sayıları kullan ve yorumla.
            - Araç/fonksiyon ÇAĞIRMA; veri yeterli.
            - "Şunu al / şunu sat" gibi DOĞRUDAN yatırım tavsiyesi VERME. Bilgilendirme ve analiz
              çerçevesinde kal (riskler, güçlü/zayıf yönler, dikkat edilecekler).
            - Kısa paragraflar + madde işaretleri kullan. Abartı yok, net ol; somut sayılara atıf yap.
            - Kısa-vade görünümde KESİN tahmin yapma; verilen Monte Carlo aralığını "olası senaryo" olarak
              aktar, belirsizliği vurgula.
            - ÜSLUP: Aynı kalıbı TEKRARLAMA. Her maddeyi "Bu, ... anlamına geliyor" / "... gösteriyor" gibi
              AYNI ifadeyle BİTİRME. Cümle yapısını çeşitlendir, doğal ve akıcı yaz; gereksiz dolgu cümle ekleme.
            - Benchmark yorumunda sana verilen ÖNDE/GERİDE etiketini KULLAN; NEGATİF farkı "daha iyi" deme
              (negatif fark = portföy GERİDE).
            - SADECE Türkçe yaz; başka dilden kelime/karakter (ör. Çince) KARIŞTIRMA.
            - Her başlığı KENDİ SATIRINDA ve **kalın** yaz (ör. "**Güçlü Yönler**").

            MUHAKEME (en önemli kural): Sayıları TEKRARLAMA — onları BİRBİRİNE BAĞLA ve NEDEN-SONUÇ kur.
            Bir metriğin diğerini nasıl açıkladığını göster; ör. "tek varlık %99 yoğunlaşma → yıllık volatilite
            %63 → 2020 stresinde -%55" aynı zincirin halkalarıdır. Her bölümde "şu sayı şudur" demek yerine
            "şu sayı, şu yüzden, şu sonucu doğuruyor" de. Önce TEŞHİS koy (en kritik bulgu), sonra gerekçelendir.

            RAPOR YAPISI (başlıkları KULLAN):
            1. **Teşhis** — portföyün EN kritik bulgusunu 1-2 cümlede SENTEZLE (sayıları bağlayarak); raporun özü.
            2. **Portföy Kimliği** — profili (Agresif/Dengeli/Korumacı) + büyüme/korumacı ağırlığını yorumla; kime uyar.
            3. **Güçlü Yönler** (madde, sayıya bağlı).
            4. **Riskler & Bağlantılar** — riskleri BİRBİRİNE BAĞLA (yoğunlaşma→volatilite→stres kaybı zinciri kur),
               sadece listeleme.
            5. **Varlık Vurguları** — öne çıkan 3-5 varlığın teknik sinyali (trend/52h/momentum) + getirisi (madde).
            6. **Benchmark & Reel Getiri** — enflasyonu yendi mi, endekslere göre ÖNDE mi GERİDE mi.
            7. **Kısa-Vade Görünüm** — Monte Carlo aralığını (medyan + %5/%95 + kayıp olasılığı) dengeli yorumla;
               kesin tahmin DEĞİL, olası senaryo.
            8. **Ne Yapılabilir (Yön)** — riski azaltma/iyileştirme YÖNÜNÜ söyle (al-sat DEĞİL; ör. "korelasyonu
               düşük bir tip eklemek yoğunlaşma riskini azaltır" gibi mantık), nihai kararın kullanıcıda olduğunu belirt.
            9. **Genel Çıkarım** (1-2 cümle, tavsiye değil değerlendirme).
            """;

    private final AssistantChatPort chatPort;
    private final StringRedisTemplate redis;

    public PortfolioAiNarrator(AssistantChatPort chatPort, StringRedisTemplate redis) {
        this.chatPort = chatPort;
        this.redis = redis;
    }

    /**
     * Hesaplanmış analiz için Türkçe yorum üretir. Cache varsa onu döner; yoksa LLM'i çağırır.
     * LLM hata verirse {@code AssistantUnavailableException} fırlatır (çağıran degrade eder).
     */
    public String generate(PortfolioAiAnalysisResult r, String userId, String userName, String userEmail) {
        String cacheKey = CACHE_PREFIX + r.getPortfolioId() + ":" + metricsHash(r);
        String cached = read(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(buildMetricsPrompt(r)));
        String reply = chatPort.complete(messages, new ToolContext(userId, userName, userEmail));
        if (reply != null && !reply.isBlank()) {
            store(cacheKey, reply);
        }
        return reply;
    }

    // ── Prompt (metrikleri kompakt metne çevir) ─────────────────────────────────

    private String buildMetricsPrompt(PortfolioAiAnalysisResult r) {
        StringBuilder b = new StringBuilder();
        b.append("PORTFÖY METRİKLERİ (hepsi backend'de hesaplandı):\n");
        b.append("- Toplam değer: ").append(money(r.getTotalValueTry())).append(" TL")
                .append(", maliyet: ").append(money(r.getTotalCostTry())).append(" TL\n");
        b.append("- Nominal getiri: %").append(pct(r.getTotalProfitLossPercent()))
                .append(" | Reel (enflasyondan arındırılmış): %").append(pct(r.getRealProfitLossPercent()))
                .append(" | Dönem TÜFE enflasyonu: %").append(pct(r.getInflationSincePercent())).append("\n");
        b.append("- Risk skoru: ").append(r.getRiskScore()).append("/100 (").append(r.getRiskLabel()).append(")\n");
        b.append("- Sağlık skoru: ").append(r.getHealthScore()).append("/100 (").append(r.getHealthLabel()).append(")\n");

        if (r.getConcentration() != null) {
            var c = r.getConcentration();
            b.append("- Yoğunlaşma: en büyük pozisyon '").append(c.topHoldingLabel()).append("' %")
                    .append(pct(c.topHoldingPercent())).append(", ilk 3 pozisyon %").append(pct(c.top3Percent()))
                    .append(" (").append(c.label()).append(")\n");
        }
        RiskMetrics m = r.getRiskMetrics();
        if (m != null && m.available()) {
            b.append("- Risk metrikleri: yıllık volatilite %").append(pct(m.annualVolatilityPercent()))
                    .append(", Sharpe ").append(num(m.sharpe()))
                    .append(", Sortino ").append(num(m.sortino()))
                    .append(", max düşüş %").append(pct(m.maxDrawdownPercent()))
                    .append(", beta ").append(num(m.beta())).append(" (").append(m.sampleMonths()).append(" ay veri)\n");
        } else {
            b.append("- Risk metrikleri: geçmiş çok kısa, hesaplanamadı.\n");
        }
        if (r.getAssetTypeAllocation() != null && !r.getAssetTypeAllocation().isEmpty()) {
            b.append("- Varlık tipi dağılımı: ");
            for (AllocationSlice s : r.getAssetTypeAllocation()) {
                b.append(s.label()).append(" %").append(pct(s.weightPercent())).append("; ");
            }
            b.append("\n");
        }
        appendAssets(b, "- En çok kazandıran", r.getTopGainers());
        appendAssets(b, "- En çok kaybettiren", r.getTopLosers());
        if (r.getBenchmarks() != null && !r.getBenchmarks().isEmpty()) {
            b.append("- Benchmark karşılaştırması (aynı parayı o araca koysaydın getirisi vs portföyün). "
                    + "ÖNDE/GERİDE etiketini OLDUĞU GİBİ kullan; negatif farka 'daha iyi' DEME:\n");
            for (BenchmarkItem bm : r.getBenchmarks()) {
                java.math.BigDecimal d = bm.deltaVsPortfolio();
                String rel = d == null ? "karşılaştırılamadı"
                        : (d.signum() >= 0 ? "portföy ÖNDE" : "portföy GERİDE");
                java.math.BigDecimal mag = d == null ? null : d.abs();
                b.append("    ").append(bm.label()).append(": %").append(pct(bm.returnPercent()))
                        .append(" → ").append(rel);
                if (mag != null) {
                    b.append(" (fark %").append(pct(mag)).append(")");
                }
                b.append("\n");
            }
        }
        if (r.getStressTests() != null && !r.getStressTests().isEmpty()) {
            b.append("- Tarihsel stres testleri (mevcut dağılımın o krizdeki tahmini etkisi):\n");
            for (StressTest st : r.getStressTests()) {
                if (st.available()) {
                    b.append("    ").append(st.label()).append(" (").append(st.period()).append("): %")
                            .append(pct(st.impactPercent())).append("\n");
                }
            }
        }
        if (r.getClassification() != null) {
            var c = r.getClassification();
            b.append("- Portföy profili: ").append(c.label()).append(" (").append(c.profile()).append(") — ")
                    .append(c.detail()).append(" Büyüme-odaklı ağırlık %").append(pct(c.growthWeightPercent()))
                    .append(", korumacı %").append(pct(c.defensiveWeightPercent())).append("\n");
        }
        if (r.getAssetSignals() != null && !r.getAssetSignals().isEmpty()) {
            b.append("- Varlık teknik sinyalleri (en büyük pozisyonlar):\n");
            int n = 0;
            for (AssetSignal s : r.getAssetSignals()) {
                if (n++ >= 8) {
                    break;
                }
                b.append("    ").append(s.name()).append(" (%").append(pct(s.weightPercent())).append("): ")
                        .append(s.note());
                if (s.profitLossPercent() != null) {
                    b.append(" — getiri %").append(pct(s.profitLossPercent()));
                }
                b.append("\n");
            }
        }
        MonteCarlo mc = r.getMonteCarlo();
        if (mc != null && mc.available()) {
            b.append("- Monte Carlo projeksiyonu (").append(mc.horizonMonths())
                    .append(" ay, lognormal/GBM): medyan değer ").append(money(mc.medianEndValue()))
                    .append(" TL (medyan getiri %").append(pct(mc.expectedReturnPercent())).append("), olası aralık %5: ")
                    .append(money(mc.p5EndValue())).append(" TL – %95: ").append(money(mc.p95EndValue()))
                    .append(" TL; nominal kayıp olasılığı %").append(pct(mc.probLossPercent()))
                    .append(". Bu kesin tahmin DEĞİL, geçmiş getiri/volatilitenin süreceği varsayımıyla olası senaryodur.\n");
        }
        b.append("\nBu metriklere göre yukarıdaki yapıda (başlıklı) bir Türkçe analiz raporu yaz.");
        return b.toString();
    }

    private void appendAssets(StringBuilder b, String label, List<AssetReturn> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        b.append(label).append(": ");
        for (AssetReturn a : list) {
            b.append(a.name()).append(" %").append(pct(a.profitLossPercent())).append("; ");
        }
        b.append("\n");
    }

    // ── Cache (best-effort) ─────────────────────────────────────────────────────

    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void store(String key, String value) {
        try {
            redis.opsForValue().set(key, value, CACHE_TTL);
        } catch (Exception e) {
            log.debug("AI analiz yorumu cache'lenemedi: {}", e.getMessage());
        }
    }

    /** Metriklerin yuvarlanmış özetinden hash — metrikler değişince yeni yorum üretilir. */
    private static int metricsHash(PortfolioAiAnalysisResult r) {
        return Objects.hash(
                r.getRiskScore(), r.getHealthScore(),
                str(r.getTotalProfitLossPercent()), str(r.getRealProfitLossPercent()),
                r.getHoldingsCount(),
                r.getConcentration() != null ? str(r.getConcentration().topHoldingPercent()) : "");
    }

    // ── Biçimleme ───────────────────────────────────────────────────────────────

    private static String money(java.math.BigDecimal v) {
        return v == null ? "—" : v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(java.math.BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }

    private static String num(java.math.BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }

    private static String str(java.math.BigDecimal v) {
        return v == null ? "" : v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
