package com.irvin.inventory.user.dto;

import com.irvin.inventory.user.Password;
import jakarta.validation.constraints.NotBlank;

// The admin hands it to the user, who must change it at their next login.
public record ResetUserPasswordRequest(@NotBlank @Password String temporaryPassword) {
}
