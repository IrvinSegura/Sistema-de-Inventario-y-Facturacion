package com.irvin.inventory.user.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.user.MobilePhone;
import com.irvin.inventory.user.Password;
import com.irvin.inventory.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Password String password,
        @NotNull Role role,
        @Schema(description = "Optional mobile number for password reset codes by SMS, e.g. +525512345678")
        @MobilePhone String phone) {

    public CreateUserRequest {
        email = Normalization.email(email);
        phone = Normalization.optionalPhone(phone);
    }
}
