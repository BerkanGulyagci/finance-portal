package com.finance.portal.portfolio.service;

import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAiNarratorTest {

    @Mock AssistantChatPort chatPort;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private PortfolioAiNarrator narrator;

    @BeforeEach
    void setUp() {
        narrator = new PortfolioAiNarrator(chatPort, redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    private static PortfolioAiAnalysisResult result() {
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

    @Test
    void generate_cacheHit_returnsCachedSkipsLlm() {
        when(valueOps.get(anyString())).thenReturn("önbellekli yorum");

        String out = narrator.generate(result(), "u1", "User", "u@x.com");

        assertThat(out).isEqualTo("önbellekli yorum");
        verify(chatPort, never()).complete(any(), any());
    }

    @Test
    void generate_cacheMiss_callsLlmAndStores() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatPort.complete(any(), any())).thenReturn("üretilmiş yorum");

        String out = narrator.generate(result(), "u1", "User", "u@x.com");

        assertThat(out).isEqualTo("üretilmiş yorum");
        verify(valueOps).set(anyString(), eq("üretilmiş yorum"), any());
    }

    @Test
    void generate_blankReply_notStored() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatPort.complete(any(), any())).thenReturn("   ");

        String out = narrator.generate(result(), "u1", "User", "u@x.com");

        assertThat(out).isEqualTo("   ");
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void generate_emptyResultFields_noNpe() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatPort.complete(any(), any())).thenReturn("ok");

        PortfolioAiAnalysisResult empty = new PortfolioAiAnalysisResult();
        empty.setPortfolioId(UUID.randomUUID());
        // Tüm BigDecimal/nested alanlar null → buildMetricsPrompt'un null-branch'leri + biçimleme "—" yolları.

        String out = narrator.generate(empty, "u1", "User", "u@x.com");
        assertThat(out).isEqualTo("ok");
    }

    @Test
    void generate_redisGetThrows_treatedAsCacheMiss() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(chatPort.complete(any(), any())).thenReturn("yorum");

        // read() Exception'ı yutar → cache miss gibi davranır → LLM çağrılır.
        String out = narrator.generate(result(), "u1", "User", "u@x.com");
        assertThat(out).isEqualTo("yorum");
    }
}
