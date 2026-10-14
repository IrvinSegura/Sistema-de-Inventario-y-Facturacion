package com.irvin.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.irvin.inventory.common.exception.AuthenticationFailedException;
import com.irvin.inventory.security.JwtProperties;
import com.irvin.inventory.security.TokenService;
import com.irvin.inventory.user.Role;
import com.irvin.inventory.user.User;
import com.irvin.inventory.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final Long USER_ID = 7L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokenService = mock(TokenService.class);

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        JwtProperties jwtProperties = new JwtProperties(
                "dGVzdC1vbmx5LWhzMjU2LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24h",
                Duration.ofMinutes(15), Duration.ofDays(7));
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, tokenService,
                jwtProperties, Clock.fixed(NOW, ZoneOffset.UTC), mock(TransactionTemplate.class));

        user = new User();
        user.setId(USER_ID);
        user.setRole(Role.SELLER);
        user.setActive(true);
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void validTokenIsRotated() {
        RefreshToken token = storedToken(NOW.plusSeconds(60));
        when(tokenService.createAccessToken(user)).thenReturn("access-token");

        assertThat(authService.refresh(RAW_TOKEN).accessToken()).isEqualTo("access-token");
        assertThat(token.getRevocationReason()).isEqualTo(RevocationReason.ROTATED);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingOtherSessions() {
        storedToken(NOW);

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN)).isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any(), any());
    }

    @Test
    void reusedRotatedTokenRevokesAllSessions() {
        storedToken(NOW.plusSeconds(60)).revoke(NOW.minusSeconds(5), RevocationReason.ROTATED);

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN)).isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID, NOW, RevocationReason.SESSIONS_REVOKED);
    }

    @Test
    void tokenRevokedByLogoutDoesNotRevokeOtherSessions() {
        storedToken(NOW.plusSeconds(60)).revoke(NOW.minusSeconds(5), RevocationReason.LOGOUT);

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN)).isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any(), eq(RevocationReason.SESSIONS_REVOKED));
    }

    @Test
    void inactiveUserCannotRefresh() {
        storedToken(NOW.plusSeconds(60));
        user.setActive(false);

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN)).isInstanceOf(AuthenticationFailedException.class);
    }

    private RefreshToken storedToken(Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(expiresAt);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        return token;
    }
}
