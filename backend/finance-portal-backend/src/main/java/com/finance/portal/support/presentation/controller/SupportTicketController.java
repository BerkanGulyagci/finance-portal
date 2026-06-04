package com.finance.portal.support.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.support.application.service.SupportTicketService;
import com.finance.portal.support.presentation.dto.CreateTicketRequest;
import com.finance.portal.support.presentation.dto.SupportTicketResponse;
import com.finance.portal.support.presentation.dto.UpdateTicketRequest;
import com.finance.portal.support.presentation.mapper.SupportTicketMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Kullanıcının kendi destek talepleri (profil → "Bir problem mi yaşıyorsunuz?").
 * Düzenleme/silme yalnızca talep "Açık" durumdayken mümkündür (servis denetler).
 */
@RestController
@RequestMapping(value = "/api/v1/support/tickets", produces = MediaType.APPLICATION_JSON_VALUE)
public class SupportTicketController {

    private final SupportTicketService service;

    public SupportTicketController(SupportTicketService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTicketRequest request) {
        String reporter = jwt.getClaimAsString("preferred_username");
        if (reporter == null || reporter.isBlank()) {
            reporter = jwt.getClaimAsString("email");
        }
        SupportTicketResponse body = SupportTicketMapper.toResponse(
                service.create(jwt.getSubject(), jwt.getClaimAsString("email"), reporter,
                        request.subject(), request.message()));
        return ResponseEntity.ok(ApiResponse.success(body, "Talebiniz alındı"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupportTicketResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        List<SupportTicketResponse> body = SupportTicketMapper.toResponseList(service.listForUser(jwt.getSubject()));
        return ResponseEntity.ok(ApiResponse.success(body, "Destek talepleri getirildi"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketRequest request) {
        SupportTicketResponse body = SupportTicketMapper.toResponse(
                service.update(jwt.getSubject(), id, request.subject(), request.message()));
        return ResponseEntity.ok(ApiResponse.success(body, "Talep güncellendi"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(jwt.getSubject(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Talep silindi"));
    }
}
