package com.finance.portal.auth.controller;

import com.finance.portal.auth.dto.RegisterRequest;
import com.finance.portal.auth.service.KeycloakAdminService;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KeycloakAdminService keycloakAdminService;

    public AuthController(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        keycloakAdminService.registerUser(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Kayıt başarılı. Giriş yapabilirsiniz."));
    }
}
