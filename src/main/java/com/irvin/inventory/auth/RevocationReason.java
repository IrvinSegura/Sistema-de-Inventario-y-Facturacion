package com.irvin.inventory.auth;

public enum RevocationReason {
    // Replaced by a newer token; reusing it means the token was stolen.
    ROTATED,
    LOGOUT,
    // Password change, role change or deactivation.
    SESSIONS_REVOKED
}
