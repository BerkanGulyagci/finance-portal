package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Porti'nin çağırabileceği bir araç (OpenAI/Groq function-calling). LLM aracın adını + argümanlarını
 * üretir; biz {@link #execute} ile mevcut servislerimizden gerçek veriyi getirip LLM'e geri veririz.
 */
public interface AssistantTool {

    /** Fonksiyon adı (LLM'in çağıracağı; snake_case). */
    String name();

    /** LLM'in ne zaman çağıracağını anlaması için kısa açıklama. */
    String description();

    /** JSON Schema (type:object, properties, required) — argüman tanımı. */
    Map<String, Object> parameters();

    /** Aracı çalıştırır, LLM'e geri verilecek düz-metin sonucu döner (hata da metin olarak). */
    String execute(JsonNode args, ToolContext ctx);
}
