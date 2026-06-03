package com.finance.portal.assistant.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort.AssistantUnavailableException;
import com.finance.portal.assistant.application.tools.ToolContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GroqChatClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate.postForEntity) stub'lanır.
 * Base-URL {@code props.setApiUrl(wm.baseUrl())} ile WireMock'a yönlendirilir; {@link ToolRegistry} Mockito mock.
 * Kapsam: yapılandırma yok / happy-path / boş yanıt / no-choices / 500 / 429-uzun-kota / 400 tool_use_failed
 * kurtarma / native tool_calls döngüsü / inline (metin) tool çağrısı / inline artık temizleme.
 * Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring context yok).
 */
class GroqChatClientTest {

    private static final String PATH = "/chat";

    private static WireMockServer wm;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private AssistantProperties props;
    private ToolRegistry toolRegistry;
    private GroqChatClient client;

    private final List<ChatMessage> messages = List.of(ChatMessage.user("Merhaba"));
    private final ToolContext ctx = new ToolContext(null, null, null);

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
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
        toolRegistry = mock(ToolRegistry.class);

        props = new AssistantProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setModel("llama-test");
        props.setApiUrl(wm.baseUrl() + PATH);
        props.setMaxTokens(256);
        props.setTemperature(0.3);

        client = new GroqChatClient(restTemplate, objectMapper, props, toolRegistry);
    }

    // ── Yapılandırma ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("completeWith: API key yoksa (isUsable=false) → AssistantUnavailableException")
    void notConfigured() {
        props.setApiKey("");
        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class);
    }

    // ── Happy path (araçsız) ──────────────────────────────────────────────────

    @Test
    @DisplayName("complete: 200 + içerikli choice → temiz metin döner")
    void complete_ok() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(
                okJson("{\"choices\":[{\"message\":{\"content\":\"Merhaba Berkan\"}}]}")));

        String out = client.complete(messages, ctx);

        assertThat(out).isEqualTo("Merhaba Berkan");
    }

    @Test
    @DisplayName("completeWith: choices boş dizi → AssistantUnavailableException")
    void noChoices() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("{\"choices\":[]}")));

        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("No choices");
    }

    @Test
    @DisplayName("completeWith: içerik boş → 'Empty completion' AssistantUnavailableException")
    void emptyContent() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(
                okJson("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}")));

        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Empty completion");
    }

    // ── HTTP hata dalları ─────────────────────────────────────────────────────

    @Test
    @DisplayName("completeWith: 500 → Provider HTTP 500 AssistantUnavailableException")
    void serverError() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Provider HTTP 500");
    }

    @Test
    @DisplayName("completeWith: 429 uzun kota (Retry-After 30s) → beklemeden fallback'e bırakır (HTTP 429)")
    void rateLimitedLongQuota() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(
                aResponse().withStatus(429).withHeader("Retry-After", "30").withBody("rate limited")));

        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Provider HTTP 429");
    }

    @Test
    @DisplayName("completeWith: 400 tool_use_failed → failed_generation içeriği kurtarılıp metin döner")
    void toolUseFailedRecovered() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        // failed_generation düz metin (içinde <function=...> yok) → temiz olarak döner.
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"tool_use_failed\","
                        + "\"failed_generation\":\"Kurtarilan yanit metni\"}}")));

        String out = client.completeWith(messages, ctx, "m");

        assertThat(out).isEqualTo("Kurtarilan yanit metni");
    }

    @Test
    @DisplayName("completeWith: 400 tool_use_failed ama failed_generation yok → Provider HTTP 400 fırlatır")
    void toolUseFailedNoGeneration() {
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"tool_use_failed\"}}")));

        assertThatThrownBy(() -> client.completeWith(messages, ctx, "m"))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("Provider HTTP 400");
    }

    // ── Araç (function-calling) döngüsü ────────────────────────────────────────

    @Test
    @DisplayName("completeWith: native tool_calls → araç çalıştırılır, 2. tur metin döner")
    void nativeToolCallLoop() {
        when(toolRegistry.isEmpty()).thenReturn(false);
        when(toolRegistry.openAiToolDefs()).thenReturn(List.of());
        when(toolRegistry.run(eq("get_current_price"), anyString(), any(ToolContext.class)))
                .thenReturn("Fiyat: 100");

        wm.stubFor(post(urlPathEqualTo(PATH)).inScenario("tools")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                        + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"get_current_price\",\"arguments\":\"{}\"}}]}}]}"))
                .willSetStateTo("answered"));

        wm.stubFor(post(urlPathEqualTo(PATH)).inScenario("tools")
                .whenScenarioStateIs("answered")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"Fiyat 100 TL\"}}]}")));

        String out = client.completeWith(messages, ctx, "m");

        assertThat(out).isEqualTo("Fiyat 100 TL");
    }

    @Test
    @DisplayName("completeWith: inline <function=...> metni → araç çalıştırılır, 2. tur metin döner")
    void inlineToolCallLoop() {
        when(toolRegistry.isEmpty()).thenReturn(false);
        when(toolRegistry.openAiToolDefs()).thenReturn(List.of());
        when(toolRegistry.run(anyString(), anyString(), any(ToolContext.class)))
                .thenReturn("ETH: 3000");

        wm.stubFor(post(urlPathEqualTo(PATH)).inScenario("inline")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":"
                        + "\"<function=get_current_price>{\\\"symbol\\\":\\\"ETH\\\"}</function>\"}}]}"))
                .willSetStateTo("answered2"));

        wm.stubFor(post(urlPathEqualTo(PATH)).inScenario("inline")
                .whenScenarioStateIs("answered2")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"ETH 3000 dolar\"}}]}")));

        String out = client.completeWith(messages, ctx, "m");

        assertThat(out).isEqualTo("ETH 3000 dolar");
    }

    @Test
    @DisplayName("completeWith: nihai yanıttaki <function=...> artığı temizlenir")
    void stripsInlineResidue() {
        // Araçlar sunulmaz (isEmpty=true) → inline parse devre dışı, ama strip her zaman uygulanır.
        when(toolRegistry.isEmpty()).thenReturn(true);
        wm.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(
                "{\"choices\":[{\"message\":{\"content\":"
                        + "\"Sonuc hazir <function=foo>{\\\"a\\\":1}</function>\"}}]}")));

        String out = client.completeWith(messages, ctx, "m");

        assertThat(out).isEqualTo("Sonuc hazir");
        assertThat(out).doesNotContain("<function=");
    }
}
