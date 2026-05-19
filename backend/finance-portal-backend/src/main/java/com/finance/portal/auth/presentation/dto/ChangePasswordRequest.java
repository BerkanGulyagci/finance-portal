package com.finance.portal.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Mevcut şifre zorunludur.")
    private String currentPassword;

    @NotBlank(message = "Yeni şifre zorunludur.")
    @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalıdır.")
    private String newPassword;

    @NotBlank(message = "Yeni şifre tekrarı zorunludur.")
    private String confirmPassword;
}
