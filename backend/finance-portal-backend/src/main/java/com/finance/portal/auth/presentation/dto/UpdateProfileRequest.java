package com.finance.portal.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Ad zorunludur.")
    @Size(max = 100, message = "Ad en fazla 100 karakter olabilir.")
    private String firstName;

    @NotBlank(message = "Soyad zorunludur.")
    @Size(max = 100, message = "Soyad en fazla 100 karakter olabilir.")
    private String lastName;
}
