package com.finance.portal.support.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.support.application.service.SupportTicketService;
import com.finance.portal.support.domain.SupportTicketStatus;
import com.finance.portal.support.presentation.dto.SupportTicketResponse;
import com.finance.portal.support.presentation.dto.UpdateTicketStatusRequest;
import com.finance.portal.support.presentation.mapper.SupportTicketMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin: kullanıcı detayında destek taleplerini görüntüleme ve durum/not güncelleme.
 * Güvenlik: /api/admin/** ROLE_ADMIN gerektirir (SecurityConfig). Durum değişince kullanıcıya bildirim gider.
 */
@RestController
@RequestMapping(value = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminSupportController {

    private final SupportTicketService service;

    public AdminSupportController(SupportTicketService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/tickets")
    public ResponseEntity<ApiResponse<List<SupportTicketResponse>>> userTickets(@PathVariable String userId) {
        List<SupportTicketResponse> body = SupportTicketMapper.toResponseList(service.listForUserAsAdmin(userId));
        return ResponseEntity.ok(ApiResponse.success(body, "Kullanıcının destek talepleri getirildi"));
    }

    @PatchMapping("/support/tickets/{id}/status")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        SupportTicketStatus status = SupportTicketStatus.fromString(request.status());
        if (status == null) {
            throw new IllegalArgumentException("Geçersiz durum: " + request.status());
        }
        SupportTicketResponse body = SupportTicketMapper.toResponse(
                service.updateStatus(id, status, request.adminNote()));
        return ResponseEntity.ok(ApiResponse.success(body, "Talep durumu güncellendi"));
    }
}
