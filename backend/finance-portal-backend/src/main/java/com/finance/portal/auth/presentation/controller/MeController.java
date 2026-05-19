package com.finance.portal.auth.presentation.controller;

import com.finance.portal.auth.application.service.MeService;
import com.finance.portal.auth.presentation.dto.MeResponse;
import com.finance.portal.common.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping
    public ResponseEntity<ApiResponse<MeResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(meService.getCurrentUser(jwt), "Profil bilgisi getirildi."));
    }
}
