package com.irvin.inventory.sale.dto;

import com.irvin.inventory.user.User;

// An active seller with no open drawer and none today, so a manager can assign them one.
public record AssignableSellerResponse(Long id, String name) {

    public static AssignableSellerResponse from(User user) {
        return new AssignableSellerResponse(user.getId(), user.getName());
    }
}
