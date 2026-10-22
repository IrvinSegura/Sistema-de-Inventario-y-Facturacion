package com.irvin.inventory.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    // Base64 of "test-only-hs256-secret-not-for-production!".
    private static final String SECRET = "dGVzdC1vbmx5LWhzMjU2LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24h";

    @Test
    void maxSessionAgeCantBeShorterThanAnAccessToken() {
        assertThatThrownBy(() -> properties(Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> properties(Duration.ofMinutes(15))).doesNotThrowAnyException();
    }

    private static JwtProperties properties(Duration maxSessionAge) {
        return new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), maxSessionAge);
    }
}
