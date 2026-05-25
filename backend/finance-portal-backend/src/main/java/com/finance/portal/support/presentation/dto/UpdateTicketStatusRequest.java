package com.finance.portal.support.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin'in talep durumunu (OPEN/IN_PROGRESS/RESOLVED) ve opsiyonel notunu güncellemesi. */
public record UpdateTicketStatusRequest(
        @NotBlank String status,
        @Size(max = 5000) String adminNote) {
}
