package com.irvin.inventory.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.irvin.inventory.auth.dto.LoginRequest;
import com.irvin.inventory.auth.dto.TokenResponse;
import com.irvin.inventory.common.exception.AuthenticationFailedException;
import com.irvin.inventory.security.JwtProperties;
import com.irvin.inventory.security.TokenService;
import com.irvin.inventory.user.User;
import com.irvin.inventory.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Lock order is always user row -> refresh token rows (same as UserService) to avoid deadlocks.
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    // Checked for unknown emails so response time doesn't reveal which accounts exist.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService, JwtProperties jwtProperties, Clock clock,
            TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // No surrounding transaction: BCrypt is slow and must not hold a pool connection.
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new AuthenticationFailedException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()) || !user.isActive()) {
            throw new AuthenticationFailedException();
        }
        return transactionTemplate.execute(status -> issueTokens(user));
    }

    // Rotation: each refresh token works once. Reusing a ROTATED token signals theft, so every
    // session of that user is revoked (kept despite the exception).
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(AuthenticationFailedException::new);
        // Serializes with password changes and user updates, so no token is issued after a revocation.
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AuthenticationFailedException::new);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(AuthenticationFailedException::new);
        Instant now = clock.instant();

        if (token.isRevoked()) {
            if (token.getRevocationReason() == RevocationReason.ROTATED) {
                refreshTokenRepository.revokeAllByUserId(userId, now, RevocationReason.SESSIONS_REVOKED);
            }
            throw new AuthenticationFailedException();
        }
        if (!token.getExpiresAt().isAfter(now) || !user.isActive()) {
            throw new AuthenticationFailedException();
        }
        token.revoke(now, RevocationReason.ROTATED);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(clock.instant(), RevocationReason.LOGOUT));
    }

    // Caller must hold the user row lock (see class comment).
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, clock.instant(), RevocationReason.SESSIONS_REVOKED);
    }

    private TokenResponse issueTokens(User user) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(clock.instant().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.bearer(
                tokenService.createAccessToken(user), jwtProperties.accessTokenTtl().toSeconds(), rawRefreshToken);
    }

    // Only the SHA-256 is stored, so a DB leak doesn't expose usable tokens (or reset codes).
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
