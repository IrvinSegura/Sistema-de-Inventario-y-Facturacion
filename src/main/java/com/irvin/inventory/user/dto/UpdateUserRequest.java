package com.irvin.inventory.user.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.TextRules;
import com.irvin.inventory.user.MobilePhone;
import com.irvin.inventory.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = TextRules.PERSON_NAME_PATTERN, message = TextRules.PERSON_NAME_MESSAGE) String name,
        @Schema(description = "New sign-in email; omit or null to keep the current one")
        @Email @Size(max = 150) String email,
        @NotNull Role role,
        @NotNull Boolean active,
        @Schema(description = "Mobile number for password reset codes by SMS; omit or null to remove it")
        @MobilePhone String phone) {

    public UpdateUserRequest {
        email = Normalization.optionalEmail(email);
        phone = Normalization.optionalPhone(phone);
    }
}
