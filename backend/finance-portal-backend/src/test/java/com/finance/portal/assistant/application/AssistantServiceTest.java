package com.finance.portal.assistant.application;

import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.assistant.application.tools.ToolContext;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock private AssistantChatPort chatPort;
    @Mock private AssistantRateLimiter rateLimiter;
    @Mock private AssistantProperties props;
    @Mock private CentralIntegrationLogService integrationLog;
    @Mock private AssistantToolUsageTracker usageTracker;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(chatPort, rateLimiter, props, integrationLog, usageTracker, redis);
        // Most "happy path" tests need a usable provider + default history/cache config.
        lenient().when(props.isUsable()).thenReturn(true);
        lenient().when(props.getHistoryLimit()).thenReturn(10);
        lenient().when(props.getMaxInputChars()).thenReturn(2000);
        lenient().when(props.getCacheTtlSeconds()).thenReturn(21600L);
        lenient().when(props.getProvider()).thenReturn("groq");
        lenient().when(props.getModel()).thenReturn("llama-3.3-70b");
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    private List<ChatMessage> oneUser(String text) {
        List<ChatMessage> l = new ArrayList<>();
        l.add(ChatMessage.user(text));
        return l;
    }

    // ── availability gate ────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: both providers unusable → UNAVAILABLE")
    void chat_unavailable() {
        when(props.isUsable()).thenReturn(false);
        when(props.isGeminiUsable()).thenReturn(false);
        AssistantService.AssistantReply reply = service.chat(oneUser("merhaba"), "u1", "Ali", "a@x.com", "1.2.3.4");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.UNAVAILABLE);
        verify(chatPort, never()).complete(any(), any());
    }

    @Test
    @DisplayName("chat: groq unusable but gemini usable → proceeds")
    void chat_geminiFallbackProceeds() {
        when(props.isUsable()).thenReturn(false);
        when(props.isGeminiUsable()).thenReturn(true);
        when(props.getCacheTtlSeconds()).thenReturn(0L); // disable cache to keep it simple
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("yanıt");
        lenient().when(usageTracker.count()).thenReturn(1);

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar kaç"), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.OK);
        assertThat(reply.reply()).isEqualTo("yanıt");
    }

    // ── input validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: null incoming → INVALID")
    void chat_nullIncoming() {
        AssistantService.AssistantReply reply = service.chat(null, "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.INVALID);
    }

    @Test
    @DisplayName("chat: empty list → INVALID")
    void chat_emptyIncoming() {
        AssistantService.AssistantReply reply = service.chat(new ArrayList<>(), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.INVALID);
    }

    @Test
    @DisplayName("chat: only blank/assistant messages → sanitized empty → INVALID")
    void chat_sanitizedEmpty() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatMessage("assistant", "merhaba")); // no user
        msgs.add(new ChatMessage("user", "   "));          // blank dropped
        AssistantService.AssistantReply reply = service.chat(msgs, "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.INVALID);
    }

    // ── Q&A cache ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: cache hit returns cached reply without provider call or rate limit")
    void chat_cacheHit() {
        when(valueOps.get(anyString())).thenReturn("önbellekli cevap");

        AssistantService.AssistantReply reply = service.chat(oneUser("DİBS nedir?"), "u1", "Ali", "a@x.com", "ip");

        assertThat(reply.status()).isEqualTo(AssistantService.Status.OK);
        assertThat(reply.reply()).isEqualTo("önbellekli cevap");
        verify(rateLimiter, never()).register(any(), any());
        verify(chatPort, never()).complete(any(), any());
    }

    @Test
    @DisplayName("chat: cache miss + no tools used → result cached")
    void chat_cacheMissThenStore() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("MA hareketli ortalamadır.");
        lenient().when(usageTracker.count()).thenReturn(0); // no tools → cacheable

        AssistantService.AssistantReply reply = service.chat(oneUser("MA nedir"), "u1", "Ali", "a@x.com", "ip");

        assertThat(reply.status()).isEqualTo(AssistantService.Status.OK);
        verify(valueOps).set(anyString(), eq("MA hareketli ortalamadır."), any());
        verify(usageTracker).reset();
        verify(usageTracker).clear();
    }

    @Test
    @DisplayName("chat: tools used → result NOT cached")
    void chat_toolUsedNotCached() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("Dolar 45 TL.");
        lenient().when(usageTracker.count()).thenReturn(2); // tools used → not cached

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar kaç"), "u1", "Ali", "a@x.com", "ip");

        assertThat(reply.status()).isEqualTo(AssistantService.Status.OK);
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("chat: cacheTtl<=0 disables key → no cache get/set")
    void chat_cacheDisabled() {
        when(props.getCacheTtlSeconds()).thenReturn(0L);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(0);

        service.chat(oneUser("x nedir"), "u1", "Ali", "a@x.com", "ip");

        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("chat: multi-turn history → no single-turn cache key (no cache lookup)")
    void chat_multiTurnNoCache() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.user("merhaba"));
        msgs.add(ChatMessage.assistant("merhaba, nasıl yardımcı olabilirim?"));
        msgs.add(ChatMessage.user("dolar kaç"));
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("45 TL");
        lenient().when(usageTracker.count()).thenReturn(0);

        service.chat(msgs, "u1", "Ali", "a@x.com", "ip");

        // size != 1 → singleTurnCacheKey null → never touches cache
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("chat: cacheGet swallows redis exception (treated as miss)")
    void chat_cacheGetException() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(1);

        AssistantService.AssistantReply reply = service.chat(oneUser("nedir bu"), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.OK);
    }

    // ── rate limiting ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: LOGIN_REQUIRED decision → LOGIN_REQUIRED status")
    void chat_loginRequired() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register(null, "ip")).thenReturn(AssistantRateLimiter.Decision.LOGIN_REQUIRED);

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar"), null, null, null, "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.LOGIN_REQUIRED);
        verify(chatPort, never()).complete(any(), any());
    }

    @Test
    @DisplayName("chat: RATE_LIMITED decision → RATE_LIMITED status")
    void chat_rateLimited() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.RATE_LIMITED);

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar"), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.RATE_LIMITED);
        verify(chatPort, never()).complete(any(), any());
    }

    // ── provider error mapping ────────────────────────────────────────────────

    @Test
    @DisplayName("chat: provider 429 → BUSY + integration log + usage cleared")
    void chat_provider429() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any()))
                .thenThrow(new AssistantChatPort.AssistantUnavailableException("HTTP 429 Too Many Requests"));

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar"), "u1", "Ali", "a@x.com", "ip");

        assertThat(reply.status()).isEqualTo(AssistantService.Status.BUSY);
        verify(integrationLog).publish(anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), anyString());
        verify(usageTracker).clear();
    }

    @Test
    @DisplayName("chat: provider non-429 error → UNAVAILABLE")
    void chat_providerOtherError() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any()))
                .thenThrow(new AssistantChatPort.AssistantUnavailableException("connection reset"));

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar"), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.UNAVAILABLE);
    }

    @Test
    @DisplayName("chat: provider error with null message → UNAVAILABLE (not BUSY)")
    void chat_providerNullMessage() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any()))
                .thenThrow(new AssistantChatPort.AssistantUnavailableException(null, new RuntimeException("x")));

        AssistantService.AssistantReply reply = service.chat(oneUser("dolar"), "u1", "Ali", "a@x.com", "ip");
        assertThat(reply.status()).isEqualTo(AssistantService.Status.UNAVAILABLE);
    }

    // ── system prompt / ToolContext wiring ────────────────────────────────────

    @Test
    @DisplayName("chat: system prompt prepended + ToolContext carries identity")
    void chat_systemPromptAndContext() {
        when(props.getCacheTtlSeconds()).thenReturn(0L);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(0);

        var msgCap = org.mockito.ArgumentCaptor.forClass(List.class);
        var ctxCap = org.mockito.ArgumentCaptor.forClass(ToolContext.class);

        service.chat(oneUser("merhaba"), "u1", "Ahmet", "ah@x.com", "ip");

        verify(chatPort).complete(msgCap.capture(), ctxCap.capture());
        @SuppressWarnings("unchecked")
        List<ChatMessage> sent = msgCap.getValue();
        assertThat(sent.get(0).role()).isEqualTo("system");
        assertThat(sent.get(0).content()).contains("Porti").contains("Ahmet");
        assertThat(sent).hasSize(2);
        ToolContext ctx = ctxCap.getValue();
        assertThat(ctx.userId()).isEqualTo("u1");
        assertThat(ctx.userName()).isEqualTo("Ahmet");
        assertThat(ctx.userEmail()).isEqualTo("ah@x.com");
    }

    @Test
    @DisplayName("chat: anonymous user → system prompt uses generic greeting")
    void chat_anonymousPrompt() {
        when(props.getCacheTtlSeconds()).thenReturn(0L);
        when(rateLimiter.register(null, "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(0);

        var msgCap = org.mockito.ArgumentCaptor.forClass(List.class);
        service.chat(oneUser("merhaba"), null, null, null, "ip");

        verify(chatPort).complete(msgCap.capture(), any());
        @SuppressWarnings("unchecked")
        List<ChatMessage> sent = msgCap.getValue();
        assertThat(sent.get(0).content()).contains("giriş yapmamış");
    }

    // ── history sanitization (via captured payload) ───────────────────────────

    @Test
    @DisplayName("chat: history trimmed to limit + roles coerced + long content truncated")
    void chat_historySanitize() {
        when(props.getHistoryLimit()).thenReturn(2);
        when(props.getMaxInputChars()).thenReturn(5);
        when(props.getCacheTtlSeconds()).thenReturn(0L);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(0);

        List<ChatMessage> msgs = Arrays.asList(
                ChatMessage.user("OLD-should-be-dropped"),
                new ChatMessage("system", "weird-role-coerced-to-user"), // becomes user
                ChatMessage.user("abcdefghij")                            // truncated to 5
        );

        var msgCap = org.mockito.ArgumentCaptor.forClass(List.class);
        service.chat(new ArrayList<>(msgs), "u1", "Ali", "a@x.com", "ip");

        verify(chatPort).complete(msgCap.capture(), any());
        @SuppressWarnings("unchecked")
        List<ChatMessage> sent = msgCap.getValue();
        // system prompt + last 2 sanitized
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).role()).isEqualTo("user"); // coerced from "system"
        assertThat(sent.get(2).content()).isEqualTo("abcde"); // truncated
    }

    @Test
    @DisplayName("chat: assistant role preserved in payload")
    void chat_assistantRolePreserved() {
        when(props.getCacheTtlSeconds()).thenReturn(0L);
        when(rateLimiter.register("u1", "ip")).thenReturn(AssistantRateLimiter.Decision.ALLOWED);
        when(chatPort.complete(any(), any())).thenReturn("ok");
        lenient().when(usageTracker.count()).thenReturn(0);

        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.assistant("Önceki yanıt"));
        msgs.add(ChatMessage.user("devam et"));

        var msgCap = org.mockito.ArgumentCaptor.forClass(List.class);
        service.chat(msgs, "u1", "Ali", "a@x.com", "ip");

        verify(chatPort).complete(msgCap.capture(), any());
        @SuppressWarnings("unchecked")
        List<ChatMessage> sent = msgCap.getValue();
        assertThat(sent.get(1).role()).isEqualTo("assistant");
        assertThat(sent.get(2).role()).isEqualTo("user");
    }

    // ── AssistantReply factory helpers ────────────────────────────────────────

    @Test
    @DisplayName("AssistantReply factory helpers")
    void replyFactories() {
        AssistantService.AssistantReply ok = AssistantService.AssistantReply.ok("hi");
        assertThat(ok.status()).isEqualTo(AssistantService.Status.OK);
        assertThat(ok.reply()).isEqualTo("hi");
        AssistantService.AssistantReply busy = AssistantService.AssistantReply.of(AssistantService.Status.BUSY);
        assertThat(busy.status()).isEqualTo(AssistantService.Status.BUSY);
        assertThat(busy.reply()).isNull();
    }
}
