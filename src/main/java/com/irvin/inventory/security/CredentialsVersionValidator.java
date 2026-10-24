package com.irvin.inventory.security;

import java.time.Instant;

import com.irvin.inventory.user.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

// Refuses (401) an access token issued before the user's credentials last changed (password, role,
// deactivation), so those take effect at once instead of when the token expires. One primary-key read
// per request. Tokens without the claim predate it and match version 0.
public class CredentialsVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ENDED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Session ended", null);

    private final UserRepository userRepository;

    public CredentialsVersionValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        // The default validators already refuse it: no need to read the database.
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            return OAuth2TokenValidatorResult.success();
        }
        long issuedWith = token.getClaims().get(TokenService.CREDENTIALS_VERSION_CLAIM) instanceof Number version
                ? version.longValue()
                : 0;
        boolean current = userRepository.findCredentialsVersionById(Long.valueOf(token.getSubject()))
                .map(version -> version == issuedWith)
                .orElse(false);
        return current ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(ENDED);
    }
}
