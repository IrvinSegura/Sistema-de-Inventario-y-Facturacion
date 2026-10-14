package com.irvin.inventory.auth.dto;

import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    public LoginRequest {
        email = Normalization.email(email);
    }
}
