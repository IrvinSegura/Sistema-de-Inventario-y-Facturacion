package com.irvin.inventory.auth;

import com.irvin.inventory.auth.dto.LoginRequest;
import com.irvin.inventory.auth.dto.PasswordResetCodeRequest;
import com.irvin.inventory.auth.dto.RefreshTokenRequest;
import com.irvin.inventory.auth.dto.ResetPasswordRequest;
import com.irvin.inventory.auth.dto.TokenResponse;
import com.irvin.inventory.security.RateLimitPolicy;
import com.irvin.inventory.security.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@SecurityRequirements // public endpoints: no bearer token
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "Log in with email and password",
            description = "429 with Retry-After when one client address sends too many attempts.")
    @RateLimited(RateLimitPolicy.LOGIN)
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Exchange a refresh token for new tokens (single use)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    // Always 204, so the response doesn't reveal whether the token existed.
    @Operation(summary = "Revoke a refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "Send a password reset code by email or SMS",
            description = "Always 202, so the response doesn't reveal whether the account exists. Nothing is sent "
                    + "for unknown or inactive accounts, SMS to an account without a phone, a new request within a "
                    + "minute of the last one or more than 5 codes an hour. A new code replaces the previous one. "
                    + "429 with Retry-After when one client address sends too many requests, whatever the account.")
    @RateLimited(RateLimitPolicy.PASSWORD_RESET_CODE)
    @PostMapping("/password-reset/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        passwordResetService.requestCode(request);
    }

    @Operation(summary = "Set a new password with a reset code",
            description = "The code works once, for 10 minutes and up to 5 wrong attempts; any failure answers 400 "
                    + "'Invalid or expired code'. Signs the user out of every session. 429 with Retry-After when "
                    + "one client address sends too many attempts, whatever the account.")
    @RateLimited(RateLimitPolicy.PASSWORD_RESET)
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }
}
