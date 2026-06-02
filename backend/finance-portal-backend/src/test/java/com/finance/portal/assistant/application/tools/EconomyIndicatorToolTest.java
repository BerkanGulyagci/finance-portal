package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * get_economy_indicator aracı: site "Türkiye Ekonomisi" panelinin kaynağı olan
 * {@link EconomyService#getSummary()} verisini konu/anahtar kelimeye göre süzüp biçimler.
 */
class EconomyIndicatorToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private EconomyService economyService;
    private EconomyIndicatorTool tool;

    @BeforeEach
    void setUp() {
        economyService = mock(EconomyService.class);
        tool = new EconomyIndicatorTool(economyService);
    }

    @Test
    @DisplayName("name() / parameters() — get_economy_indicator + opsiyonel indicator")
    @SuppressWarnings("unchecked")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_economy_indicator");
        assertThat(tool.description()).contains("faiz").contains("enflasyon");

        Map<String, Object> schema = tool.parameters();
        assertThat((List<String>) schema.get("required")).isEmpty();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKey("indicator");
    }

    @Test
    @DisplayName("execute: 'ülke faizi' → RATES kategorisinin tüm göstergeleri (enflasyon HARİÇ)")
    void execute_faiz_returnsRatesCategory() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{\"indicator\":\"ülke faizi\"}"), null);

        assertThat(r)
                .contains("Politika Faizi")
                .contains("TL Mevduat Faizi")
                .contains("İhtiyaç Kredisi Faizi")
                .doesNotContain("TÜFE")
                .doesNotContain("İşsizlik");
    }

    @Test
    @DisplayName("execute: politika faizi değeri birim(%) + dönem + yıllık değişimle biçimlenir")
    void execute_formatsValueUnitPeriod() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{\"indicator\":\"politika faizi\"}"), null);

        assertThat(r)
                .contains("Politika Faizi (1 Hafta Repo): 46%")
                .contains("(dönem: 22-05-2026)")
                .contains("Kaynak: TCMB EVDS");
    }

    @Test
    @DisplayName("execute: 'enflasyon' → INFLATION kategorisi (TÜFE+ÜFE), faiz değil")
    void execute_enflasyon_returnsInflationCategory() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{\"indicator\":\"enflasyon\"}"), null);

        assertThat(r)
                .contains("TÜFE")
                .contains("Yİ-ÜFE")
                .doesNotContain("Politika Faizi");
    }

    @Test
    @DisplayName("execute: 'tüfe' → yalnız TÜFE (ÜFE'yi yanlışlıkla getirmez)")
    void execute_tufe_matchesOnlyTufe() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{\"indicator\":\"tüfe\"}"), null);

        assertThat(r).contains("TÜFE").doesNotContain("Yİ-ÜFE");
    }

    @Test
    @DisplayName("execute: filtre yok → öne çıkan göstergelerin özeti")
    void execute_noFilter_returnsHeadline() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{}"), null);

        assertThat(r)
                .contains("Politika Faizi")
                .contains("TÜFE")
                .contains("İşsizlik")
                .contains("Dolar / TL");
    }

    @Test
    @DisplayName("execute: eşleşmeyen terim → öne çıkanlara düşer + uyarı")
    void execute_unknown_fallsBackToHeadline() {
        when(economyService.getSummary()).thenReturn(sample());

        String r = tool.execute(node("{\"indicator\":\"kuantum\"}"), null);

        assertThat(r)
                .contains("doğrudan eşleşme yok")
                .contains("Politika Faizi");
    }

    @Test
    @DisplayName("execute: kullanılamayan gösterge → '(güncel veri yok)'")
    void execute_unavailableIndicator_marked() {
        EconomyIndicator unavailable = ind("politikaFaizi", "Politika Faizi (1 Hafta Repo)", "RATES", "%", null, null);
        unavailable.setAvailable(false);
        when(economyService.getSummary()).thenReturn(List.of(unavailable));

        String r = tool.execute(node("{\"indicator\":\"faiz\"}"), null);

        assertThat(r).contains("Politika Faizi").contains("güncel veri yok");
    }

    @Test
    @DisplayName("execute: servis hata fırlatırsa → dostça mesaj")
    void execute_serviceThrows_friendly() {
        when(economyService.getSummary()).thenThrow(new RuntimeException("EVDS down"));

        String r = tool.execute(node("{\"indicator\":\"faiz\"}"), null);

        assertThat(r).isEqualTo("Ekonomik göstergeler şu an alınamadı.");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static List<EconomyIndicator> sample() {
        EconomyIndicator politika = ind("politikaFaizi", "Politika Faizi (1 Hafta Repo)", "RATES", "%",
                bd("46"), "22-05-2026");
        politika.setYoyChangePercent(bd("-4"));
        return List.of(
                politika,
                ind("mevduatFaizi", "TL Mevduat Faizi", "RATES", "%", bd("50.5"), "16-05-2026"),
                ind("ihtiyacKredisiFaizi", "İhtiyaç Kredisi Faizi", "RATES", "%", bd("65.2"), "16-05-2026"),
                indYoy("tufe", "TÜFE — Tüketici Enflasyonu", "INFLATION", "endeks", bd("1850.3"), "2026-4", "35.4"),
                indYoy("ufe", "Yİ-ÜFE — Üretici Enflasyonu", "INFLATION", "endeks", bd("2100.1"), "2026-4", "28.1"),
                ind("issizlik", "İşsizlik Oranı", "LABOR", "%", bd("8.6"), "2026-3"),
                indYoy("gsyihBuyume", "GSYİH (Reel, Zincirlenmiş Hacim)", "GROWTH", "bin TL", bd("1234567"), "2026-Q1", "3.2"),
                ind("usdTry", "Dolar / TL", "FX", "TL", bd("41.2"), "29-05-2026"));
    }

    private static EconomyIndicator ind(String key, String label, String category, String unit,
                                        BigDecimal value, String period) {
        EconomyIndicator i = new EconomyIndicator();
        i.setKey(key);
        i.setLabel(label);
        i.setCategory(category);
        i.setUnit(unit);
        i.setValue(value);
        i.setPeriod(period);
        i.setAvailable(value != null);
        return i;
    }

    private static EconomyIndicator indYoy(String key, String label, String category, String unit,
                                           BigDecimal value, String period, String yoy) {
        EconomyIndicator i = ind(key, label, category, unit, value, period);
        i.setYoyChangePercent(bd(yoy));
        return i;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
