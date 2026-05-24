package com.finance.portal.alarm.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Alarm görünüm DTO'su (frontend için).
 */
public record AlarmResponse(
        UUID id,
        String assetType,
        String symbol,
        String instrumentName,
        String metric,
        String direction,
        BigDecimal threshold,
        String frequency,
        String status,
        BigDecimal lastObservedValue,
        String currencySymbol,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime triggeredAt,
        LocalDateTime lastTriggeredAt
) {
}
