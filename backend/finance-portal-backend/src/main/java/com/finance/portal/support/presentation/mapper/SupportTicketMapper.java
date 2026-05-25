package com.finance.portal.support.presentation.mapper;

import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.presentation.dto.SupportTicketResponse;

import java.util.List;
import java.util.stream.Collectors;

public final class SupportTicketMapper {

    private SupportTicketMapper() {
    }

    public static SupportTicketResponse toResponse(SupportTicket t) {
        return new SupportTicketResponse(
                t.getId() != null ? t.getId().toString() : null,
                t.getSubject(),
                t.getMessage(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getStatus() != null ? t.getStatus().getLabel() : null,
                t.getAdminNote(),
                t.getUserEmail(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
    }

    public static List<SupportTicketResponse> toResponseList(List<SupportTicket> list) {
        return list.stream().map(SupportTicketMapper::toResponse).collect(Collectors.toList());
    }
}
