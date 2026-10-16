package com.irvin.inventory.security;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Token bucket per client address and policy: up to `capacity` requests in a burst, then one more
// every `refill-period`.
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @Valid @NotNull Limit login,
        @Valid @NotNull Limit passwordResetCode,
        @Valid @NotNull Limit passwordReset) {

    public Limit limitFor(RateLimitPolicy policy) {
        return switch (policy) {
            case LOGIN -> login;
            case PASSWORD_RESET_CODE -> passwordResetCode;
            case PASSWORD_RESET -> passwordReset;
        };
    }

    public record Limit(@Positive int capacity, @NotNull Duration refillPeriod) {

        public Limit {
            if (refillPeriod != null && !refillPeriod.isPositive()) {
                throw new IllegalArgumentException("refill-period must be positive, got: " + refillPeriod);
            }
        }
    }
}
