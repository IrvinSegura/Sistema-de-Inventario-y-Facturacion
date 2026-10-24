package com.irvin.inventory.user.dto;

import java.time.Instant;

import com.irvin.inventory.user.Role;
import com.irvin.inventory.user.User;

public record UserResponse(
        Long id, String name, String email, String phone, Role role, boolean active,
        // The password is a temporary one an admin set: everything but changing it answers 403.
        boolean passwordChangeRequired,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.isActive(), user.isPasswordChangeRequired(), user.getCreatedAt());
    }
}
