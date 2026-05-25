package com.finance.portal.assistant.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.assistant.application.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini istemcisi (fallback) — Gemini'nin kendi generateContent formatı + function-calling döngüsü.
 * {@link ToolRegistry}'deki araçları paylaşır. Groq kota/hata verince {@link CompositeAssistantChatPort} çağırır.
 */
@Component
public class GeminiChatClient implements AssistantChatPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatClient.class);
    private static final int MAX_TOOL_ROUNDS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AssistantProperties props;
    private final ToolRegistry toolRegistry;

    public GeminiChatClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                            AssistantProperties props, ToolRegistry toolRegistry) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    public boolean isUsable() {
        return props.isGeminiUsable();
    }

    @Override
    public String complete(List<ChatMessage> messages, ToolContext ctx) {
        return completeWith(messages, ctx, props.getGeminiModel());
    }

    /** Belirtilen Gemini modeliyle çalıştırır (model zinciri için). */
    public String completeWith(List<ChatMessage> messages, ToolContext ctx, String model) {
        if (!props.isGeminiUsable()) {
            throw new AssistantUnavailableException("Gemini not configured (missing key)");
        }

        String systemPrompt = null;
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessage m : messages) {
            if ("system".equals(m.role())) {
                systemPrompt = m.content();
                continue;
            }
            String role = "assistant".equals(m.role()) ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", m.content()))));
        }

        boolean toolUsed = false;
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            boolean offerTools = !toolRegistry.isEmpty() && round < MAX_TOOL_ROUNDS && !toolUsed;
            JsonNode content = callOnce(systemPrompt, contents, offerTools, model);
            JsonNode parts = content.path("parts");

            List<JsonNode> functionCalls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            if (parts.isArray()) {
                for (JsonNode p : parts) {
                    if (p.has("functionCall")) {
                        functionCalls.add(p.get("functionCall"));
                    } else if (p.has("text")) {
                        text.append(p.get("text").asText(""));
                    }
                }
            }

            if (offerTools && !functionCalls.isEmpty()) {
                // Modelin functionCall içeriğini geçmişe ekle, sonra her araç için functionResponse ekle.
                contents.add(objectMapper.convertValue(content, Map.class));
                for (JsonNode fc : functionCalls) {
                    String name = fc.path("name").asText("");
                    JsonNode argsNode = fc.path("args");
                    String result = toolRegistry.run(name, argsNode, ctx);
                    contents.add(Map.of("role", "user", "parts", List.of(
                            Map.of("functionResponse", Map.of(
                                    "name", name,
                                    "response", Map.of("result", result))))));
                }
                toolUsed = true;
                continue;
            }

            String out = text.toString().trim();
            if (out.isBlank()) {
                throw new AssistantUnavailableException("Empty Gemini completion");
            }
            return out;
        }
        throw new AssistantUnavailableException("Gemini tool loop exceeded");
    }

    /** Tek bir generateContent çağrısı; candidates[0].content düğümünü döner. */
    private JsonNode callOnce(String systemPrompt, List<Map<String, Object>> contents, boolean offerTools, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        // "thinking" modelleri (gemini-2.5-*) çıktı bütçesinin bir kısmını düşünmeye harcar →
        // yanıtın kesilmemesi için en az 1024 token ver.
        int maxOut = Math.max(props.getMaxTokens(), 1024);
        body.put("generationConfig", Map.of(
                "temperature", props.getTemperature(),
                "maxOutputTokens", maxOut));
        if (offerTools) {
            body.put("tools", List.of(Map.of("functionDeclarations", toolRegistry.geminiFunctionDeclarations())));
        }

        String url = props.getGeminiApiUrl() + "/" + model
                + ":generateContent?key=" + props.getGeminiApiKey();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new AssistantUnavailableException("No candidates in Gemini response");
            }
            return candidates.get(0).path("content");
        } catch (AssistantUnavailableException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("Gemini provider HTTP {} : {}", e.getStatusCode(),
                    e.getResponseBodyAsString().replaceAll("\\s+", " ").trim());
            throw new AssistantUnavailableException("Gemini HTTP " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.warn("Gemini provider call failed: {}", e.getMessage());
            throw new AssistantUnavailableException("Gemini call failed", e);
        }
    }
}
