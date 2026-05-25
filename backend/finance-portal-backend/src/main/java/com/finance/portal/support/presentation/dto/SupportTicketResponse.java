package com.finance.portal.support.presentation.dto;

public record SupportTicketResponse(
        String id,
        String subject,
        String message,
        String status,
        String statusLabel,
        String adminNote,
        String userEmail,
        String createdAt,
        String updatedAt) {
}
