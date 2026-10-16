package com.irvin.inventory.security;

// @PreAuthorize expressions shared by controllers, so the role rules live in one place.
public final class Roles {

    // Store management: catalog, stock, purchases, cancellations.
    public static final String MANAGERS = "hasAnyRole('ADMIN', 'SUPERVISOR')";
    // User accounts.
    public static final String ADMIN = "hasRole('ADMIN')";

    private Roles() {
    }
}
