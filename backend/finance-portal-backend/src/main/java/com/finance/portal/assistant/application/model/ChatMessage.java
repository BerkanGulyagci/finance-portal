package com.finance.portal.assistant.application.model;

/**
 * Tek bir sohbet mesajı (OpenAI-uyumlu). role: "system" | "user" | "assistant".
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content)    { return new ChatMessage("system", content); }
    public static ChatMessage user(String content)       { return new ChatMessage("user", content); }
    public static ChatMessage assistant(String content)  { return new ChatMessage("assistant", content); }
}
