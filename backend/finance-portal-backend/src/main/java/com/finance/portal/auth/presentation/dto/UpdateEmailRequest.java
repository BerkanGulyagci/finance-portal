package com.finance.portal.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmailRequest {

    @NotBlank(message = "Email zorunludur.")
    @Email(message = "Geçerli bir email adresi girin.")
    private String email;
}
