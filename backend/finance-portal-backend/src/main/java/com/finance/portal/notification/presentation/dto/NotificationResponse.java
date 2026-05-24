package com.finance.portal.notification.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        UUID relatedAlarmId,
        boolean read,
        String recipientEmail,
        String emailStatus,
        String emailError,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
}
