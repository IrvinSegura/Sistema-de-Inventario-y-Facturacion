package com.irvin.inventory.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.irvin.inventory.auth.AuthService;
import com.irvin.inventory.auth.PasswordResetService;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.user.dto.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

// The unique email constraint decides when two requests pass existsByEmail at once (the ITs can't race it).
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository, mock(AccountEventRecorder.class),
            mock(AuthService.class), mock(PasswordResetService.class), mock(PasswordEncoder.class),
            mock(ApplicationEventPublisher.class));
    private final CreateUserRequest request =
            new CreateUserRequest("Ana", "ana@test.com", "user-password", Role.SELLER, null);

    @Test
    void aRaceOnTheEmailConstraintIsAConflict() {
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("could not execute",
                new RuntimeException("duplicate key value violates unique constraint \"uk_app_user_email\"")));

        assertThatThrownBy(() -> userService.create(request, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void otherConstraintViolationsAreNotRewritten() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("could not execute",
                new RuntimeException("new row violates check constraint \"ck_app_user_email_lower\""));
        when(userRepository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> userService.create(request, 1L)).isSameAs(other);
    }
}
