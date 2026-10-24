package com.irvin.inventory.user.dto;

import com.irvin.inventory.user.User;

public record SellerResponse(Long id, String name, boolean active) {

    public static SellerResponse from(User user) {
        return new SellerResponse(user.getId(), user.getName(), user.isActive());
    }
}
