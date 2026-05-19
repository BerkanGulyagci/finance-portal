package com.finance.portal.auth.presentation.controller;

import com.finance.portal.auth.application.service.MeProfileService;
import com.finance.portal.auth.application.service.MeService;
import com.finance.portal.auth.presentation.dto.ChangePasswordRequest;
import com.finance.portal.auth.presentation.dto.MeActionResponse;
import com.finance.portal.auth.presentation.dto.MeResponse;
import com.finance.portal.auth.presentation.dto.UpdateEmailRequest;
import com.finance.portal.auth.presentation.dto.UpdateProfileRequest;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final MeProfileService meProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<MeResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(meService.getCurrentUser(jwt), "Profil bilgisi getirildi."));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<MeActionResponse>> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        MeActionResponse result = meProfileService.updateProfile(jwt, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Profil bilgileriniz güncellendi."));
    }

    @PatchMapping("/email")
    public ResponseEntity<ApiResponse<MeActionResponse>> updateEmail(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateEmailRequest request
    ) {
        MeActionResponse result = meProfileService.updateEmail(jwt, request);
        return ResponseEntity.ok(ApiResponse.success(
                result,
                "Email adresiniz değiştirildi. Yeni email adresinizi doğrulamanız gerekiyor."
        ));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<MeActionResponse>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        MeActionResponse result = meProfileService.changePassword(jwt, request);
        return ResponseEntity.ok(ApiResponse.success(
                result,
                "Şifreniz güncellendi. Güvenlik için tekrar giriş yapın."
        ));
    }
}
