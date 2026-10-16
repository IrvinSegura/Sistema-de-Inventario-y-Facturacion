package com.irvin.inventory.security;

// Public endpoints limited per client address; each has its own bucket (see RateLimitProperties).
public enum RateLimitPolicy {
    LOGIN,
    PASSWORD_RESET_CODE,
    PASSWORD_RESET
}
