package com.irvin.inventory.auth.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.user.Password;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code,
        @NotBlank @Password String newPassword) {

    public ResetPasswordRequest {
        email = Normalization.email(email);
    }
}
