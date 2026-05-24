package com.finance.portal.notification.presentation.mapper;

import com.finance.portal.notification.domain.Notification;
import com.finance.portal.notification.presentation.dto.NotificationResponse;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType() != null ? n.getType().name() : null,
                n.getTitle(),
                n.getBody(),
                n.getRelatedAlarmId(),
                n.isRead(),
                n.getRecipientEmail(),
                n.getEmailStatus() != null ? n.getEmailStatus().name() : null,
                n.getEmailError(),
                n.getCreatedAt(),
                n.getSentAt()
        );
    }
}
