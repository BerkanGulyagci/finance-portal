package com.finance.portal.assistant.presentation.dto;

import com.finance.portal.assistant.application.model.ChatMessage;

import java.util.List;

/**
 * Frontend'in gönderdiği sohbet geçmişi. Son eleman kullanıcının yeni sorusudur.
 * role: "user" | "assistant" (system frontend'den KABUL EDİLMEZ; backend ekler).
 */
public record AssistantChatRequest(List<ChatMessage> messages) {
}
