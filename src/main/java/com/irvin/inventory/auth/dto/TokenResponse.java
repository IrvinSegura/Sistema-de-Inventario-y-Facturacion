package com.irvin.inventory.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds, String refreshToken) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, refreshToken);
    }
}
