package com.finance.portal.auth.presentation.controller;

import com.finance.portal.auth.application.service.RegistrationService;
import com.finance.portal.auth.presentation.dto.RegisterRequest;
import com.finance.portal.auth.presentation.mapper.AuthPresentationMapper;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthPresentationMapper authMapper;

    public AuthController(RegistrationService registrationService,
                          AuthPresentationMapper authMapper) {
        this.registrationService = registrationService;
        this.authMapper = authMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(authMapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Kayıt başarılı. Giriş yapabilirsiniz."));
    }
}
