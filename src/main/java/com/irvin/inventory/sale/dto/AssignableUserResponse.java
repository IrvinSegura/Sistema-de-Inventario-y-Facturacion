package com.irvin.inventory.sale.dto;

import com.irvin.inventory.user.Role;
import com.irvin.inventory.user.User;

// An active user with no open drawer and none today, so a manager can assign them one.
public record AssignableUserResponse(Long id, String name, Role role) {

    public static AssignableUserResponse from(User user) {
        return new AssignableUserResponse(user.getId(), user.getName(), user.getRole());
    }
}
