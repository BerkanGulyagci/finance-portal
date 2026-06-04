package com.finance.portal.market.infrastructure.external.viop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IsYatirimViopSpecClient} — VadeliIslemler JSON parse + Türkçe sayı dönüşümü.
 * RestTemplate mock'lanır (gerçek ağ yok); gerçek API formatında örnek JSON kullanılır.
 */
class IsYatirimViopSpecClientTest {

    private RestTemplate restTemplate;
    private IsYatirimViopSpecClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new IsYatirimViopSpecClient(restTemplate, new ObjectMapper(),
                mock(CentralIntegrationLogService.class));
    }

    private void stubResponse(String body) {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    // ── parseTrNumber (statik) ──────────────────────────────────────────────────

    @Test
    @DisplayName("parseTrNumber: TR formatı (binlik nokta + virgül ondalık)")
    void parseTrNumber() {
        assertThat(IsYatirimViopSpecClient.parseTrNumber("1000")).isEqualByComparingTo("1000");
        assertThat(IsYatirimViopSpecClient.parseTrNumber("5362,674")).isEqualByComparingTo("5362.674");
        assertThat(IsYatirimViopSpecClient.parseTrNumber("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(IsYatirimViopSpecClient.parseTrNumber("220,8")).isEqualByComparingTo("220.8");
    }

    @Test
    @DisplayName("parseTrNumber: null/boş/geçersiz → null")
    void parseTrNumberInvalid() {
        assertThat(IsYatirimViopSpecClient.parseTrNumber(null)).isNull();
        assertThat(IsYatirimViopSpecClient.parseTrNumber("")).isNull();
        assertThat(IsYatirimViopSpecClient.parseTrNumber("   ")).isNull();
        assertThat(IsYatirimViopSpecClient.parseTrNumber("abc")).isNull();
    }

    // ── fetchSpecs (parse) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("gerçek API formatını parse eder (Title → multiplier + teminat)")
    void parsesRealFormat() {
        stubResponse("""
            {
              "timestamp": "6/4/2026 4:20:03 PM",
              "value": [
                {
                  "Title": "F_USDTRY0626",
                  "DAYANAK_VARLIK": "D_USDTRY",
                  "SOZLESME_BUYUKLUGU": "1000",
                  "UZLASMA_TIPI": "Cash Settlement",
                  "PARA_BIRIMI": "TRY",
                  "BASLANGIC_TEMINATI": "5362,674",
                  "SOZLESME_TURU": "Future"
                },
                {
                  "Title": "F_AKBNK0626",
                  "DAYANAK_VARLIK": "D_AKBNK",
                  "SOZLESME_BUYUKLUGU": "100",
                  "UZLASMA_TIPI": "Physical Settlement",
                  "PARA_BIRIMI": "TRY",
                  "BASLANGIC_TEMINATI": "996,45",
                  "SOZLESME_TURU": "Future"
                }
              ]
            }
            """);

        Map<String, IsYatirimViopSpecClient.IsYatirimViopSpec> specs = client.fetchSpecs();

        assertThat(specs).hasSize(2).containsKeys("F_USDTRY0626", "F_AKBNK0626");

        IsYatirimViopSpecClient.IsYatirimViopSpec usd = specs.get("F_USDTRY0626");
        assertThat(usd.multiplier()).isEqualByComparingTo("1000");
        assertThat(usd.marginAmount()).isEqualByComparingTo("5362.674");
        assertThat(usd.currency()).isEqualTo("TRY");
        assertThat(usd.physical()).isFalse(); // Cash Settlement

        IsYatirimViopSpecClient.IsYatirimViopSpec akbnk = specs.get("F_AKBNK0626");
        assertThat(akbnk.multiplier()).isEqualByComparingTo("100");
        assertThat(akbnk.marginAmount()).isEqualByComparingTo("996.45");
        assertThat(akbnk.physical()).isTrue(); // Physical Settlement
    }

    @Test
    @DisplayName("büyüklük yok/0 olan kayıt atlanır (spec anlamsız)")
    void skipsRecordsWithoutMultiplier() {
        stubResponse("""
            {
              "value": [
                { "Title": "F_OK0626", "SOZLESME_BUYUKLUGU": "100", "BASLANGIC_TEMINATI": "50", "PARA_BIRIMI": "TRY", "UZLASMA_TIPI": "Cash" },
                { "Title": "F_NOSIZE0626", "BASLANGIC_TEMINATI": "50", "PARA_BIRIMI": "TRY" },
                { "Title": "F_ZERO0626", "SOZLESME_BUYUKLUGU": "0", "BASLANGIC_TEMINATI": "50" }
              ]
            }
            """);

        Map<String, IsYatirimViopSpecClient.IsYatirimViopSpec> specs = client.fetchSpecs();
        assertThat(specs).hasSize(1).containsKey("F_OK0626");
    }

    @Test
    @DisplayName("teminat null olabilir (büyüklük yeterli)")
    void marginAmountNullable() {
        stubResponse("""
            { "value": [ { "Title": "F_X0626", "SOZLESME_BUYUKLUGU": "10", "PARA_BIRIMI": "TRY", "UZLASMA_TIPI": "Cash" } ] }
            """);
        Map<String, IsYatirimViopSpecClient.IsYatirimViopSpec> specs = client.fetchSpecs();
        assertThat(specs).hasSize(1);
        assertThat(specs.get("F_X0626").multiplier()).isEqualByComparingTo("10");
        assertThat(specs.get("F_X0626").marginAmount()).isNull();
    }

    @Test
    @DisplayName("boş body / 'value' yok → boş map (çökmez)")
    void emptyOrMissingValue() {
        stubResponse("");
        assertThat(client.fetchSpecs()).isEmpty();

        stubResponse("{\"timestamp\":\"x\"}"); // value yok
        assertThat(client.fetchSpecs()).isEmpty();
    }

    @Test
    @DisplayName("RestTemplate exception → boş map (çökmez)")
    void networkFailure() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));
        assertThat(client.fetchSpecs()).isEmpty();
    }
}
