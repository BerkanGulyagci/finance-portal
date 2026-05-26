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
 * OpenAI-uyumlu chat-completions istemcisi (varsayılan Groq) + function-calling döngüsü.
 * LLM bir araç çağırırsa, aracı yerel servislerle çalıştırıp sonucu geri besler ve tekrar sorar
 * (en fazla {@link #MAX_TOOL_ROUNDS} tur). Sağlayıcıyı değiştirmek için sadece env değişir.
 */
@Component
public class GroqChatClient implements AssistantChatPort {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final int MAX_TOOL_ROUNDS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AssistantProperties props;
    private final ToolRegistry toolRegistry;

    public GroqChatClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                          AssistantProperties props, ToolRegistry toolRegistry) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String complete(List<ChatMessage> messages, ToolContext ctx) {
        return completeWith(messages, ctx, props.getModel());
    }

    /** Belirtilen modelle çalıştırır (model zinciri için). */
    public String completeWith(List<ChatMessage> messages, ToolContext ctx, String model) {
        if (!props.isUsable()) {
            throw new AssistantUnavailableException("Assistant not configured (missing API key)");
        }

        // Çalışan mesaj listesi (Map; araç turlarında assistant tool_calls + tool sonuçları eklenir).
        List<Map<String, Object>> working = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", m.role());
            map.put("content", m.content());
            working.add(map);
        }

        // Bir araç çalıştıktan sonra araçları TEKRAR sunma: 2. çağrı küçülür (token tasarrufu, free TPM),
        // model sonucu kullanıp yanıt verir, gereksiz tekrar tool çağrısı olmaz.
        boolean toolUsed = false;
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            boolean offerTools = !toolRegistry.isEmpty() && round < MAX_TOOL_ROUNDS && !toolUsed;
            JsonNode message = callOnce(working, offerTools, model);
            JsonNode toolCalls = message.path("tool_calls");

            // 1) Native tool_calls
            if (offerTools && toolCalls.isArray() && !toolCalls.isEmpty()) {
                working.add(objectMapper.convertValue(message, Map.class));
                for (JsonNode call : toolCalls) {
                    String id = call.path("id").asText("");
                    String fnName = call.path("function").path("name").asText("");
                    String argsRaw = call.path("function").path("arguments").asText("{}");
                    appendToolResult(working, id, toolRegistry.run(fnName, argsRaw, ctx));
                }
                toolUsed = true;
                continue;
            }

            String content = message.path("content").asText("");

            // 2) Bazı Llama modelleri aracı native yerine METİN olarak döküyor:
            //    <function=get_current_price>{"asset_type":"CRYPTO","symbol":"ETH"}</function>
            //    Bunu yakala, gerçek araç gibi çalıştır, sonucu geri besle.
            List<ToolInvocation> inline = offerTools ? extractInlineCalls(content) : List.of();
            if (!inline.isEmpty()) {
                List<Map<String, Object>> synthCalls = new ArrayList<>();
                for (ToolInvocation inv : inline) {
                    synthCalls.add(Map.of("id", inv.id(), "type", "function",
                            "function", Map.of("name", inv.name(), "arguments", inv.argsRaw())));
                }
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", "");
                assistantMsg.put("tool_calls", synthCalls);
                working.add(assistantMsg);
                for (ToolInvocation inv : inline) {
                    appendToolResult(working, inv.id(), toolRegistry.run(inv.name(), inv.argsRaw(), ctx));
                }
                toolUsed = true;
                continue;
            }

            // 3) Gerçek metin yanıtı — kalan <function...> artıklarını temizleyip döndür.
            String clean = stripInlineCalls(content).trim();
            if (clean.isBlank()) {
                throw new AssistantUnavailableException("Empty completion from provider");
            }
            return clean;
        }
        throw new AssistantUnavailableException("Tool loop exceeded");
    }

    private static void appendToolResult(List<Map<String, Object>> working, String toolCallId, String result) {
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", toolCallId);
        toolMsg.put("content", result);
        working.add(toolMsg);
    }

    private record ToolInvocation(String id, String name, String argsRaw) {}

    // Llama'nın native olmayan formatları: <function=name>{...}</function> VE bozuk varyant <function=name{...}
    // ( '>' eksik veya kapanış etiketi eksik olabilir — Groq bunu 400 tool_use_failed ile döndürür).
    private static final java.util.regex.Pattern INLINE_FN = java.util.regex.Pattern.compile(
            "<function=([A-Za-z0-9_]+)\\s*>?\\s*(\\{.*?\\})\\s*(?:</function>)?", java.util.regex.Pattern.DOTALL);

    private String extractFailedGeneration(String body) {
        try {
            return objectMapper.readTree(body).path("error").path("failed_generation").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** İçerikteki metin-formatlı araç çağrılarını ayıklar (Llama'nın native olmayan formatı için yedek). */
    private static List<ToolInvocation> extractInlineCalls(String content) {
        if (content == null || content.indexOf("<function=") < 0) {
            return List.of();
        }
        List<ToolInvocation> out = new ArrayList<>();
        java.util.regex.Matcher m = INLINE_FN.matcher(content);
        int i = 0;
        while (m.find()) {
            out.add(new ToolInvocation("call_inline_" + (++i), m.group(1), m.group(2)));
        }
        return out;
    }

    /** Nihai yanıttan kalan <function...> metin artıklarını siler (kullanıcı ham çağrı görmesin). */
    private static String stripInlineCalls(String content) {
        if (content == null) {
            return "";
        }
        return INLINE_FN.matcher(content).replaceAll("").replaceAll("<function=[^>}]*[>}]?", "").trim();
    }

    /** Tek bir chat-completions çağrısı; choices[0].message düğümünü döner. 429'da bir kez bekleyip yeniden dener. */
    private JsonNode callOnce(List<Map<String, Object>> working, boolean offerTools, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        body.put("stream", false);
        body.put("messages", working);
        if (offerTools) {
            body.put("tools", toolRegistry.openAiToolDefs());
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        for (int attempt = 0; ; attempt++) {
            try {
                ResponseEntity<String> resp = restTemplate.postForEntity(props.getApiUrl(), entity, String.class);
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    throw new AssistantUnavailableException("No choices in provider response");
                }
                return choices.get(0).path("message");
            } catch (AssistantUnavailableException e) {
                throw e;
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                // 429: kısa bekleme (dakikalık TPM dolgunluğu) ise bir kez bekleyip dene; uzun bekleme
                // (~günlük kota) ise boşuna bekleme — hata fırlat, Composite Gemini fallback'ine geçsin.
                if (code == 429 && attempt < 1) {
                    long waitMs = parseRetryMs(e);
                    if (waitMs <= 9000) {
                        log.debug("Assistant 429 (TPM) — {}ms bekleyip yeniden denenecek", waitMs);
                        sleep(Math.max(500, waitMs));
                        continue;
                    }
                    log.debug("Assistant 429 (~günlük kota, {}ms) — fallback'e bırakılıyor", waitMs);
                }
                String bodyStr = e.getResponseBodyAsString();
                // Groq, model bozuk-formatlı araç çağrısı üretince 400 "tool_use_failed" döner ve ham metni
                // failed_generation'da verir. Bunu kurtarıp content gibi döndür → ana döngü inline parse eder.
                if (code == 400 && bodyStr.contains("tool_use_failed")) {
                    String failed = extractFailedGeneration(bodyStr);
                    if (failed != null && !failed.isBlank()) {
                        log.debug("Assistant tool_use_failed kurtarıldı: {}", failed);
                        com.fasterxml.jackson.databind.node.ObjectNode synthetic = objectMapper.createObjectNode();
                        synthetic.put("role", "assistant");
                        synthetic.put("content", failed);
                        return synthetic;
                    }
                }
                log.warn("Assistant provider HTTP {} : {}", e.getStatusCode(),
                        bodyStr.replaceAll("\\s+", " ").trim());
                throw new AssistantUnavailableException("Provider HTTP " + code, e);
            } catch (Exception e) {
                log.warn("Assistant provider call failed: {}", e.getMessage());
                throw new AssistantUnavailableException("Provider call failed", e);
            }
        }
    }

    /** 429 yanıtından önerilen ham bekleme süresini (ms) çıkarır (Retry-After header ya da gövdedeki "try again in Xs"). */
    private static long parseRetryMs(org.springframework.web.client.HttpStatusCodeException e) {
        long ms = 1500;
        try {
            var headers = e.getResponseHeaders();
            String ra = headers != null ? headers.getFirst("Retry-After") : null;
            if (ra != null && !ra.isBlank()) {
                ms = (long) (Double.parseDouble(ra.trim()) * 1000);
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("try again in ([0-9.]+)s").matcher(e.getResponseBodyAsString());
                if (m.find()) {
                    ms = (long) (Double.parseDouble(m.group(1)) * 1000) + 300;
                }
            }
        } catch (Exception ignored) {
            // varsayılan
        }
        return ms;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
