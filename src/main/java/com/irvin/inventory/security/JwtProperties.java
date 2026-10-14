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
        @NotNull Duration refreshTokenTtl) {

    // HS256 requires a key of at least 256 bits.
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && !secret.isBlank() && decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.jwt.secret must be Base64 of at least 32 bytes");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(decode(secret), "HmacSHA256");
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
