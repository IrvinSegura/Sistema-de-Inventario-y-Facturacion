package com.irvin.inventory.user;

import com.irvin.inventory.security.Roles;
import java.net.URI;

import com.irvin.inventory.user.dto.AccountEventResponse;
import com.irvin.inventory.user.dto.CreateUserRequest;
import com.irvin.inventory.user.dto.ResetUserPasswordRequest;
import com.irvin.inventory.user.dto.UpdateUserRequest;
import com.irvin.inventory.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "User management (ADMIN only)")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize(Roles.ADMIN)
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a user")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UserResponse user = userService.create(request, Long.valueOf(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @Operation(summary = "List users, by name unless sorted",
            description = "Sort by name, email, role or createdAt.")
    @GetMapping
    public Page<UserResponse> findAll(@ParameterObject Pageable pageable) {
        return userService.findAll(pageable);
    }

    @Operation(summary = "Get a user")
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @Operation(summary = "Update name, email, role and status; role or status changes revoke sessions",
            description = "A new email invalidates the user's pending password reset codes; 409 if another "
                    + "account has it.")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.update(id, request, Long.valueOf(jwt.getSubject()));
    }

    @Operation(summary = "A user's account history, newest first",
            description = "Who created the account and changed its email, role, status or password, and when.")
    @GetMapping("/{id}/events")
    public Page<AccountEventResponse> findEvents(@PathVariable Long id, @ParameterObject Pageable pageable) {
        return userService.findEvents(id, pageable);
    }

    @Operation(summary = "Reset a user's password to a temporary one",
            description = "Ends all of the user's sessions; until they change it, every endpoint but their "
                    + "profile, their password change and logout answers 403. 400 for the admin's own account.")
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetUserPassword(@PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resetUserPassword(id, request, Long.valueOf(jwt.getSubject()));
    }
}
