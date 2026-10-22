package com.irvin.inventory.security;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        // Counted from the login, however often the session refreshes.
        @NotNull Duration maxSessionAge) {

    // HS256 requires a key of at least 256 bits.
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && !secret.isBlank() && decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.jwt.secret must be Base64 of at least 32 bytes");
        }
        // The last access token still outlives the session by up to its TTL; a shorter session would
        // end at that token anyway.
        if (maxSessionAge != null && accessTokenTtl != null && maxSessionAge.compareTo(accessTokenTtl) < 0) {
            throw new IllegalArgumentException(
                    "app.jwt.max-session-age must be at least app.jwt.access-token-ttl, got: " + maxSessionAge);
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(decode(secret), "HmacSHA256");
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
