package com.finance.portal.assistant.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.AssistantToolUsageTracker;
import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort.AssistantUnavailableException;
import com.finance.portal.assistant.application.tools.AssistantTool;
import com.finance.portal.assistant.application.tools.ToolContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link GeminiChatClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate.postForEntity) stub'lanır.
 * URL {@code geminiApiUrl} + model üzerinden kurulduğundan, base-URL WireMock'a yönlendirilir.
 * Kapsanan dallar: yapılandırılmamış (key yok) → exception, happy-path (text), system-prompt dalı,
 * boş-yanıt, candidates-yok, HTTP 500/429 hataları, ve araç (functionCall) döngüsü.
 * Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring yok).
 */
class GeminiChatClientTest {

    private static WireMockServer wm;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeAll
    static void startWireMock() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wm != null) wm.stop();
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Gemini fallback "kullanılabilir" (key dolu) ve base-URL WireMock'a yönlenmiş props. */
    private AssistantProperties usableProps() {
        AssistantProperties p = new AssistantProperties();
        p.setFallbackEnabled(true);
        p.setGeminiApiKey("test-key");
        p.setGeminiApiUrl(wm.baseUrl());
        p.setGeminiModel("gemini-2.5-flash");
        return p;
    }

    private ToolRegistry emptyRegistry() {
        return new ToolRegistry(List.of(), objectMapper, new AssistantToolUsageTracker());
    }

    private ToolRegistry registryWithOneTool() {
        AssistantTool tool = new AssistantTool() {
            @Override public String name() { return "get_price"; }
            @Override public String description() { return "Bir varlığın fiyatını döner."; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of("symbol", Map.of("type", "string")),
                        "required", List.of("symbol"));
            }
            @Override public String execute(JsonNode args, ToolContext ctx) {
                return "BTC: 100000 USD";
            }
        };
        return new ToolRegistry(List.of(tool), objectMapper, new AssistantToolUsageTracker());
    }

    private GeminiChatClient client(AssistantProperties props, ToolRegistry registry) {
        return new GeminiChatClient(restTemplate, objectMapper, props, registry);
    }

    private static final ToolContext CTX = new ToolContext(null, null, null);

    private static String textResponse(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}";
    }

    // ── isUsable / not-configured ───────────────────────────────────────────

    @Test
    @DisplayName("isUsable: key dolu → true, key boş → false")
    void isUsable_reflectsProps() {
        assertThat(client(usableProps(), emptyRegistry()).isUsable()).isTrue();

        AssistantProperties noKey = usableProps();
        noKey.setGeminiApiKey("");
        assertThat(client(noKey, emptyRegistry()).isUsable()).isFalse();
    }

    @Test
    @DisplayName("complete: Gemini yapılandırılmamış (key yok) → AssistantUnavailableException")
    void complete_notConfigured() {
        AssistantProperties noKey = usableProps();
        noKey.setGeminiApiKey("");
        GeminiChatClient client = client(noKey, emptyRegistry());

        assertThatThrownBy(() -> client.complete(List.of(ChatMessage.user("merhaba")), CTX))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete: 200 + text → trimlenmiş metin döner (model props.getGeminiModel())")
    void complete_ok_returnsText() {
        wm.stubFor(post(anyUrl()).willReturn(okJson(textResponse("  Merhaba Berkan  "))));

        String out = client(usableProps(), emptyRegistry())
                .complete(List.of(ChatMessage.user("selam")), CTX);

        assertThat(out).isEqualTo("Merhaba Berkan");
    }

    @Test
    @DisplayName("completeWith: system mesajı systemInstruction dalını tetikler, yanıt döner")
    void completeWith_systemPrompt() {
        wm.stubFor(post(anyUrl()).willReturn(okJson(textResponse("Yanit"))));

        String out = client(usableProps(), emptyRegistry()).completeWith(
                List.of(ChatMessage.system("Sen bir finans asistanısın"),
                        ChatMessage.assistant("Önceki yanıt"),
                        ChatMessage.user("soru")),
                CTX, "gemini-2.5-flash-lite");

        assertThat(out).isEqualTo("Yanit");
    }

    // ── empty / no-candidates branches ──────────────────────────────────────

    @Test
    @DisplayName("complete: 200 ama text boş → 'Empty Gemini completion'")
    void complete_blankText() {
        wm.stubFor(post(anyUrl()).willReturn(okJson(textResponse("   "))));

        GeminiChatClient client = client(usableProps(), emptyRegistry());
        assertThatThrownBy(() -> client.complete(List.of(ChatMessage.user("x")), CTX))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Empty Gemini completion");
    }

    @Test
    @DisplayName("complete: candidates yok → 'No candidates' (callOnce dalı)")
    void complete_noCandidates() {
        wm.stubFor(post(anyUrl()).willReturn(okJson("{\"candidates\":[]}")));

        GeminiChatClient client = client(usableProps(), emptyRegistry());
        assertThatThrownBy(() -> client.complete(List.of(ChatMessage.user("x")), CTX))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("No candidates");
    }

    // ── HTTP error branches ─────────────────────────────────────────────────

    @Test
    @DisplayName("complete: HTTP 500 → 'Gemini HTTP 500' (HttpStatusCodeException dalı)")
    void complete_serverError() {
        wm.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(500).withBody("server error")));

        GeminiChatClient client = client(usableProps(), emptyRegistry());
        assertThatThrownBy(() -> client.complete(List.of(ChatMessage.user("x")), CTX))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Gemini HTTP 500");
    }

    @Test
    @DisplayName("complete: HTTP 429 rate-limit → 'Gemini HTTP 429'")
    void complete_rateLimited() {
        wm.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(429).withBody("rate limited")));

        GeminiChatClient client = client(usableProps(), emptyRegistry());
        assertThatThrownBy(() -> client.complete(List.of(ChatMessage.user("x")), CTX))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Gemini HTTP 429");
    }

    // ── tool (functionCall) loop ────────────────────────────────────────────

    @Test
    @DisplayName("complete: model functionCall döner → araç çalışır → ikinci turda metin döner")
    void complete_toolLoop() {
        String functionCallResp = "{\"candidates\":[{\"content\":{\"parts\":[{"
                + "\"functionCall\":{\"name\":\"get_price\",\"args\":{\"symbol\":\"BTC\"}}}]}}]}";

        wm.stubFor(post(anyUrl())
                .inScenario("tool")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(functionCallResp))
                .willSetStateTo("called"));
        wm.stubFor(post(anyUrl())
                .inScenario("tool")
                .whenScenarioStateIs("called")
                .willReturn(okJson(textResponse("BTC fiyati 100000 USD"))));

        String out = client(usableProps(), registryWithOneTool())
                .complete(List.of(ChatMessage.user("BTC fiyatı ne?")), CTX);

        assertThat(out).isEqualTo("BTC fiyati 100000 USD");
        wm.verify(2, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(anyUrl()));
    }
}
