package com.irvin.inventory.user.dto;

import com.irvin.inventory.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Role role,
        @NotNull Boolean active) {
}
