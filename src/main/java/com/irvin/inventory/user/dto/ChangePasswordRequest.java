package com.irvin.inventory.user.dto;

import com.irvin.inventory.user.Password;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Password String newPassword) {
}
