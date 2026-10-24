package com.irvin.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.irvin.inventory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CredentialsVersionValidatorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CredentialsVersionValidator validator = new CredentialsVersionValidator(userRepository);

    @Test
    void theCurrentVersionPassesAndAnOlderOneDoesNot() {
        when(userRepository.findCredentialsVersionById(7L)).thenReturn(Optional.of(3));

        assertThat(validator.validate(token(3L, Instant.now().plusSeconds(60))).hasErrors()).isFalse();
        assertThat(validator.validate(token(2L, Instant.now().plusSeconds(60))).hasErrors()).isTrue();
        // However the parser types the number.
        assertThat(validator.validate(token(3, Instant.now().plusSeconds(60))).hasErrors()).isFalse();
    }

    @Test
    void aTokenWithoutTheClaimMatchesVersionZero() {
        when(userRepository.findCredentialsVersionById(7L)).thenReturn(Optional.of(0));

        assertThat(validator.validate(token(null, Instant.now().plusSeconds(60))).hasErrors()).isFalse();
    }

    @Test
    void aDeletedUserIsRefused() {
        when(userRepository.findCredentialsVersionById(7L)).thenReturn(Optional.empty());

        assertThat(validator.validate(token(0L, Instant.now().plusSeconds(60))).hasErrors()).isTrue();
    }

    @Test
    void anExpiredTokenIsLeftToTheDefaultValidators() {
        assertThat(validator.validate(token(0L, Instant.now().minusSeconds(60))).hasErrors()).isFalse();
        verify(userRepository, never()).findCredentialsVersionById(any());
    }

    private static Jwt token(Number version, Instant expiresAt) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt);
        if (version != null) {
            builder.claim(TokenService.CREDENTIALS_VERSION_CLAIM, version);
        }
        return builder.build();
    }
}
