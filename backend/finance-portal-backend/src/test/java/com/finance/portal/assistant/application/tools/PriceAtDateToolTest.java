package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PriceAtDateToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioHistoricalPricePort pricePort;
    private PriceAtDateTool tool;

    @BeforeEach
    void setUp() {
        pricePort = mock(PortfolioHistoricalPricePort.class);
        tool = new PriceAtDateTool(pricePort);
    }

    @Test
    @DisplayName("name() / parameters() — get_price_at_date + required asset_type/symbol/date")
    @SuppressWarnings("unchecked")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_price_at_date");
        java.util.Map<String, Object> schema = tool.parameters();
        assertThat((java.util.List<String>) schema.get("required"))
                .containsExactlyInAnyOrder("asset_type", "symbol", "date");
    }

    /**
     * 4 farklı geçersiz/eksik input türü — her biri kendi açıklayıcı mesajını üretmeli
     * ve pricePort'a hiç gitmemeli. Sonar S5976: aynı şekildeki testler parameterized.
     */
    @ParameterizedTest(name = "execute: {2} → ''{1}''")
    @CsvSource({
            "'{\"asset_type\":\"STOCK\",\"date\":\"2026-01-10\"}',                                 'Sembol ve tarih gereklidir', eksik symbol",
            "'{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}',                                    'Sembol ve tarih gereklidir', eksik date",
            "'{\"asset_type\":\"GARBAGE\",\"symbol\":\"X\",\"date\":\"2026-01-10\"}',              'Geçersiz varlık türü',        gecersiz asset_type",
            "'{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"date\":\"not-a-date\"}',            'Geçersiz tarih',              bozuk tarih formati"
    })
    void execute_invalidInput_returnsFriendlyErrorAndSkipsPort(String payload, String expectedMessage, String label) {
        String r = tool.execute(node(payload), null);

        assertThat(r).as(label).contains(expectedMessage);
        verifyNoInteractions(pricePort);
    }

    @Test
    @DisplayName("execute: gelecek tarih → reddedilir")
    void execute_futureDate_rejected() {
        LocalDate future = LocalDate.now().plusDays(30);
        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\",\"date\":\""
                + future + "\"}"), null);

        assertThat(r).contains("Gelecek bir tarih");
        verifyNoInteractions(pricePort);
    }

    @Test
    @DisplayName("execute: seri boş → 'veri bulunamadı' mesajı")
    void execute_emptySeries_noDataMessage() {
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"date\":\"2025-12-10\"}"), null);

        assertThat(r).contains("fiyat verisi bulunamadı");
    }

    @Test
    @DisplayName("execute: o güne ait kapanış var → '<sembol> — <tarih> kapanışı ≈ <fiyat> TL.'")
    void execute_exactDateMatch_returnsClose() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 10), new BigDecimal("3060.190000"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.GOLD), eq("GRAM"), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\",\"date\":\"2025-01-10\"}"), null);

        assertThat(r)
                .contains("GRAM")
                .contains("2025-01-10")
                .contains("3060.19")                  // stripTrailingZeros uygulanmış
                .doesNotContain("en yakın iş günü");  // tam eşleşme
    }

    @Test
    @DisplayName("execute: hafta sonu / tatil → en yakın önceki iş gününe düşer (floorEntry)")
    void execute_floorsToPreviousBusinessDay() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 10), new BigDecimal("3060.19"));   // Cuma
        // İstenen 11 Ocak (Cumartesi); 10 Ocak'a düşmeli
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\",\"date\":\"2025-01-11\"}"), null);

        assertThat(r)
                .contains("2025-01-10")
                .contains("en yakın iş günü");
    }

    @Test
    @DisplayName("execute: tarih string 10 karakterden uzun (ISO datetime) → ilk 10 karakteri kullanır")
    void execute_longDateString_truncatedToYmd() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 1, 10), new BigDecimal("100"));
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenReturn(Optional.of(series));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"date\":\"2025-01-10T15:30:00\"}"), null);

        assertThat(r)
                .contains("THYAO")
                .contains("2025-01-10");
    }

    @Test
    @DisplayName("execute: price port exception → kullanıcı dostu hata")
    void execute_portThrows_friendlyMessage() {
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("upstream down"));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"date\":\"2025-01-10\"}"), null);

        assertThat(r).contains("tarihsel fiyat alınamadı");
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
