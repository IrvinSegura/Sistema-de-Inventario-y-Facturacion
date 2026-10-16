package com.irvin.inventory.auth.dto;

import com.irvin.inventory.auth.ResetChannel;
import com.irvin.inventory.common.Normalization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PasswordResetCodeRequest(
        @NotBlank @Email String email,
        @NotNull ResetChannel channel,
        @Schema(description = "Language of the message: es or en (default)")
        @Pattern(regexp = "es|en") String language) {

    public PasswordResetCodeRequest {
        email = Normalization.email(email);
        language = language == null ? "en" : language;
    }
}
