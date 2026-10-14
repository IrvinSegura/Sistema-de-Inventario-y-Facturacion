package com.irvin.inventory.user.dto;

import java.time.Instant;

import com.irvin.inventory.user.Role;
import com.irvin.inventory.user.User;

public record UserResponse(Long id, String name, String email, Role role, boolean active, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
