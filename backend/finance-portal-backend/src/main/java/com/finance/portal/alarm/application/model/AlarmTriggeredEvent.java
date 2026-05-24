package com.finance.portal.alarm.application.model;

import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.common.domain.AssetType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alarm koşulu sağlandığında üretilen olay. {@code AlarmNotificationPort} bunu alıp
 * uygulama-içi bildirim + e-posta üretir.
 */
public record AlarmTriggeredEvent(
        String userId,
        String recipientEmail,
        UUID alarmId,
        AssetType assetType,
        String symbol,
        String instrumentName,
        AlarmMetric metric,
        AlarmDirection direction,
        BigDecimal threshold,
        BigDecimal observedValue,
        String note
) {
}
