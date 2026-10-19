package com.irvin.inventory.security;

import com.irvin.inventory.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

// Authenticated user as stated by the access token (sub = user id, role claim).
public record CurrentUser(Long id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(TokenService.ROLE_CLAIM)));
    }

    // Admins and supervisors see every seller's data and may change what sellers can't.
    public boolean isManager() {
        return role == Role.ADMIN || role == Role.SUPERVISOR;
    }
}
