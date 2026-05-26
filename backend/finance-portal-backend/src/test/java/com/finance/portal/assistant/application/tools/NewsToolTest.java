package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.service.NewsAggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NewsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private NewsAggregatorService aggregator;
    private NewsTool tool;

    @BeforeEach
    void setUp() {
        aggregator = mock(NewsAggregatorService.class);
        tool = new NewsTool(aggregator);
    }

    @Test
    @DisplayName("name() / parameters() — get_news + opsiyonel parametreler")
    @SuppressWarnings("unchecked")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_news");
        assertThat(tool.description()).contains("haber").contains("ECONOMY");

        java.util.Map<String, Object> schema = tool.parameters();
        assertThat((List<String>) schema.get("required")).isEmpty();  // hepsi opsiyonel
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("category", "query");
    }

    @Test
    @DisplayName("execute: birden çok haber → '• başlık (kaynak)' satırlarıyla birleşik")
    void execute_listsItems_withBulletsAndSource() {
        when(aggregator.query(any(), any(), any(), eq("TR"), any(), any(), eq(1), eq(5), any()))
                .thenReturn(new NewsQueryResult(List.of(
                        new NewsArticle("Merkez bankası faiz kararını açıkladı", null, null, null, null, "BloombergHT", null),
                        new NewsArticle("BIST 100 günü yükselişle kapattı", null, null, null, null, "Anadolu", null)
                ), 1, 5, 2L, 1, List.of(), Map.of()));

        String r = tool.execute(node("{}"), null);

        assertThat(r).startsWith("Güncel başlıklar:");
        assertThat(r).contains("• Merkez bankası");
        assertThat(r).contains("(BloombergHT)");
        assertThat(r).contains("• BIST 100");
        assertThat(r).contains("(Anadolu)");
    }

    @Test
    @DisplayName("execute: kategori ECONOMY → enum ile aggregator çağrılır")
    void execute_categoryEconomy_passedAsEnumName() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(new NewsQueryResult(List.of(), 1, 5, 0L, 0, List.of(), Map.of()));

        tool.execute(node("{\"category\":\"ECONOMY\"}"), null);

        ArgumentCaptor<String> catCap = ArgumentCaptor.forClass(String.class);
        verify(aggregator).query(catCap.capture(), any(), any(), eq("TR"), any(), any(), eq(1), eq(5), any());
        assertThat(catCap.getValue()).isEqualTo("ECONOMY");
    }

    @Test
    @DisplayName("execute: query parametresi varsa aggregator'a iletilir")
    void execute_query_passedThrough() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(new NewsQueryResult(List.of(), 1, 5, 0L, 0, List.of(), Map.of()));

        tool.execute(node("{\"query\":\"dolar\"}"), null);

        verify(aggregator).query(any(), any(), eq("dolar"), any(), any(), any(), eq(1), eq(5), any());
    }

    @Test
    @DisplayName("execute: boş query → null gönderilir (aggregator default)")
    void execute_blankQuery_passedAsNull() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(new NewsQueryResult(List.of(), 1, 5, 0L, 0, List.of(), Map.of()));

        tool.execute(node("{\"query\":\"  \"}"), null);

        verify(aggregator).query(any(), any(), eq(null), any(), any(), any(), eq(1), eq(5), any());
    }

    @Test
    @DisplayName("execute: sonuç boş → 'Şu an gösterilecek haber bulunamadı.'")
    void execute_empty_friendlyMessage() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(new NewsQueryResult(List.of(), 1, 5, 0L, 0, List.of(), Map.of()));

        String r = tool.execute(node("{}"), null);

        assertThat(r).isEqualTo("Şu an gösterilecek haber bulunamadı.");
    }

    @Test
    @DisplayName("execute: aggregator null result döndürürse → boş haber mesajı")
    void execute_nullResult_friendlyMessage() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(null);

        String r = tool.execute(node("{}"), null);

        assertThat(r).isEqualTo("Şu an gösterilecek haber bulunamadı.");
    }

    @Test
    @DisplayName("execute: aggregator exception fırlatırsa → 'Haberler alınamadı.'")
    void execute_aggregatorThrows_errorMessage() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenThrow(new RuntimeException("RSS down"));

        String r = tool.execute(node("{}"), null);

        assertThat(r).isEqualTo("Haberler alınamadı.");
    }

    @Test
    @DisplayName("execute: boş başlık atlanır (içerikte • yok)")
    void execute_blankTitleSkipped() {
        when(aggregator.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class), any()))
                .thenReturn(new NewsQueryResult(List.of(
                        new NewsArticle("", null, null, null, null, "X", null),
                        new NewsArticle("Geçerli başlık", null, null, null, null, "Y", null)
                ), 1, 5, 2L, 1, List.of(), Map.of()));

        String r = tool.execute(node("{}"), null);

        assertThat(r).contains("• Geçerli başlık");
        assertThat(r).contains("(Y)");
        assertThat(r).doesNotContain("(X)");
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
