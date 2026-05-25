package com.finance.portal.assistant.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.assistant.application.AssistantToolUsageTracker;
import com.finance.portal.assistant.application.tools.AssistantTool;
import com.finance.portal.assistant.application.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tüm {@link AssistantTool} bean'lerini toplar; hem OpenAI/Groq hem Gemini function-calling şekline dönüştürür
 * ve aracı çalıştırır. Groq ve Gemini istemcileri bunu paylaşır (DRY).
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AssistantTool> tools = new LinkedHashMap<>();
    private final List<Map<String, Object>> openAiDefs = new ArrayList<>();
    private final List<Map<String, Object>> geminiDecls = new ArrayList<>();
    private final ObjectMapper objectMapper;
    private final AssistantToolUsageTracker usageTracker;

    public ToolRegistry(List<AssistantTool> toolList, ObjectMapper objectMapper,
                        AssistantToolUsageTracker usageTracker) {
        this.objectMapper = objectMapper;
        this.usageTracker = usageTracker;
        for (AssistantTool t : toolList) {
            tools.put(t.name(), t);
            openAiDefs.add(Map.of("type", "function", "function", Map.of(
                    "name", t.name(), "description", t.description(), "parameters", t.parameters())));
            // Gemini: parametresiz araçlarda boş "properties" gönderme (bazı sürümler reddeder).
            Map<String, Object> decl = new LinkedHashMap<>();
            decl.put("name", t.name());
            decl.put("description", t.description());
            Object props = t.parameters() != null ? t.parameters().get("properties") : null;
            if (props instanceof Map<?, ?> pm && !pm.isEmpty()) {
                decl.put("parameters", t.parameters());
            }
            geminiDecls.add(decl);
        }
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /** OpenAI/Groq "tools" dizisi. */
    public List<Map<String, Object>> openAiToolDefs() {
        return openAiDefs;
    }

    /** Gemini "functionDeclarations" dizisi. */
    public List<Map<String, Object>> geminiFunctionDeclarations() {
        return geminiDecls;
    }

    /** Aracı çalıştırır; LLM'e geri verilecek düz-metin sonucu döner (hata da metin). */
    public String run(String name, JsonNode args, ToolContext ctx) {
        AssistantTool tool = tools.get(name);
        if (tool == null) {
            return "Bilinmeyen araç: " + name;
        }
        try {
            usageTracker.increment();
            String result = tool.execute(args != null ? args : objectMapper.createObjectNode(), ctx);
            log.info("Porti araç çağrısı: {}({}) -> {}", name, args,
                    result != null && result.length() > 120 ? result.substring(0, 120) + "…" : result);
            return result != null ? result : "(boş sonuç)";
        } catch (Exception e) {
            log.debug("Tool {} failed: {}", name, e.getMessage());
            return name + " aracı çalıştırılamadı.";
        }
    }

    /** JSON string argümanı parse edip çalıştırır (Groq tool_calls / inline için). */
    public String run(String name, String argsRaw, ToolContext ctx) {
        try {
            JsonNode args = objectMapper.readTree(argsRaw == null || argsRaw.isBlank() ? "{}" : argsRaw);
            return run(name, args, ctx);
        } catch (Exception e) {
            return name + " argümanları çözülemedi.";
        }
    }
}
