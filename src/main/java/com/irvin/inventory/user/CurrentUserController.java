package com.irvin.inventory.user;

import com.irvin.inventory.user.dto.ChangePasswordRequest;
import com.irvin.inventory.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Endpoints any authenticated user can call on their own account.
@Tag(name = "Current user")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class CurrentUserController {

    private final UserService userService;

    @Operation(summary = "Get the authenticated user")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.findById(userId(jwt));
    }

    @Operation(summary = "Change own password; revokes all sessions")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId(jwt), request);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
