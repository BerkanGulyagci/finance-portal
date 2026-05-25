package com.finance.portal.assistant.presentation.dto;

/**
 * Asistan yanıtı. status: OK | LOGIN_REQUIRED | RATE_LIMITED | UNAVAILABLE | INVALID.
 * reply yalnızca OK'te dolu; diğer durumlarda frontend status'a göre mesaj gösterir.
 */
public record AssistantChatResponse(String status, String reply) {
}
