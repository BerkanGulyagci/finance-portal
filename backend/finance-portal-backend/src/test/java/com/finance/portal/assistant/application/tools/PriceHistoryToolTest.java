package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PriceHistoryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioHistoricalPricePort pricePort;
    private PriceHistoryTool tool;

    @BeforeEach
    void setUp() {
        pricePort = mock(PortfolioHistoricalPricePort.class);
        tool = new PriceHistoryTool(pricePort);
    }

    @Test
    @DisplayName("name() / parameters() — get_price_history + required asset_type & symbol")
    @SuppressWarnings("unchecked")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_price_history");
        java.util.Map<String, Object> schema = tool.parameters();
        assertThat((java.util.List<String>) schema.get("required"))
                .containsExactlyInAnyOrder("asset_type", "symbol");
    }

    @Test
    @DisplayName("execute: eksik symbol → uyarı")
    void execute_missingSymbol_complains() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\"}"), null);

        assertThat(r).contains("Sembol gereklidir");
        verifyNoInteractions(pricePort);
    }

    @Test
    @DisplayName("execute: geçersiz asset_type → açıklayıcı hata")
    void execute_invalidAssetType_rejected() {
        String r = tool.execute(node("{\"asset_type\":\"BAD\",\"symbol\":\"X\"}"), null);

        assertThat(r).contains("Geçersiz varlık türü");
        verifyNoInteractions(pricePort);
    }

    @Test
    @DisplayName("execute: 1 nokta seri → 'yeterli tarihsel veri bulunamadı'")
    void execute_insufficientData() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 1), new BigDecimal("100"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).contains("yeterli tarihsel veri");
    }

    @Test
    @DisplayName("execute: kısa seri (5 nokta) → başlangıç/güncel/getiri + min/max var; MA/RSI yok")
    void execute_shortSeries_basicMetrics() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        // 5 nokta — MA20 ve RSI(14) hesaplanamaz
        series.put(LocalDate.of(2025, 1, 1), new BigDecimal("100"));
        series.put(LocalDate.of(2025, 1, 2), new BigDecimal("110"));
        series.put(LocalDate.of(2025, 1, 3), new BigDecimal("105"));
        series.put(LocalDate.of(2025, 1, 4), new BigDecimal("120"));
        series.put(LocalDate.of(2025, 1, 5), new BigDecimal("130"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).contains("THYAO");
        assertThat(r).contains("başlangıç").contains("güncel");
        // Getiri = (130-100)/100 = 30% → "30.00%" (fmt 2 ondalık ama strip → "30")
        assertThat(r).contains("dönem getirisi 30");
        assertThat(r).contains("min 100").contains("max 130");
        assertThat(r).doesNotContain("MA20");
        assertThat(r).doesNotContain("RSI");
    }

    @Test
    @DisplayName("execute: 25 nokta → MA20 hesaplanır, fiyat MA20'nin altında → 'altında'")
    void execute_ma20Computed_priceBelow() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        // 25 nokta, son fiyat ortalama altında düşürelim
        BigDecimal[] vals = new BigDecimal[]{
                BigDecimal.valueOf(120), BigDecimal.valueOf(125), BigDecimal.valueOf(130), BigDecimal.valueOf(128),
                BigDecimal.valueOf(135), BigDecimal.valueOf(140), BigDecimal.valueOf(138), BigDecimal.valueOf(142),
                BigDecimal.valueOf(145), BigDecimal.valueOf(150), BigDecimal.valueOf(148), BigDecimal.valueOf(152),
                BigDecimal.valueOf(155), BigDecimal.valueOf(158), BigDecimal.valueOf(160), BigDecimal.valueOf(162),
                BigDecimal.valueOf(165), BigDecimal.valueOf(168), BigDecimal.valueOf(170), BigDecimal.valueOf(165),
                BigDecimal.valueOf(160), BigDecimal.valueOf(150), BigDecimal.valueOf(140), BigDecimal.valueOf(130),
                BigDecimal.valueOf(110)  // final low
        };
        for (int i = 0; i < vals.length; i++) {
            series.put(LocalDate.of(2025, 1, 1).plusDays(i), vals[i]);
        }
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"X\",\"range\":\"3M\"}"), null);

        assertThat(r).contains("MA20");
        assertThat(r).contains("(fiyat altında)");        // son 110, MA20 daha yüksek
    }

    @Test
    @DisplayName("execute: 50+ nokta → MA20 + MA50 + RSI(14) görünür")
    void execute_longSeries_allIndicators() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        // 60 nokta — random walk benzeri
        for (int i = 0; i < 60; i++) {
            series.put(LocalDate.of(2025, 1, 1).plusDays(i),
                    new BigDecimal(100 + i % 7));
        }
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\",\"range\":\"1Y\"}"), null);

        assertThat(r).contains("MA20");
        assertThat(r).contains("MA50");
        assertThat(r).contains("RSI(14)");
    }

    @Test
    @DisplayName("execute: range varsayılan 1Y (boş bırakılırsa)")
    void execute_defaultRange_isOneYear() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 1), new BigDecimal("100"));
        series.put(LocalDate.of(2025, 1, 2), new BigDecimal("110"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\"}"), null);

        assertThat(r).contains("(1Y)");
    }

    @Test
    @DisplayName("execute: 5Y range → uzun dönem geçmişi")
    void execute_fiveYearRange() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 1), new BigDecimal("100"));
        series.put(LocalDate.of(2025, 1, 2), new BigDecimal("110"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\",\"range\":\"5Y\"}"), null);

        assertThat(r).contains("(5Y)");
    }

    @Test
    @DisplayName("execute: port exception → 'teknik veri alınamadı'")
    void execute_portThrows_friendlyMessage() {
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("upstream down"));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).contains("teknik veri alınamadı");
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
