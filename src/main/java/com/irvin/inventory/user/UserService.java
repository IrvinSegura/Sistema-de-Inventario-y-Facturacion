package com.irvin.inventory.user;

import java.util.List;
import java.util.Map;

import com.irvin.inventory.auth.AuthService;
import com.irvin.inventory.common.Sorting;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.user.dto.ChangePasswordRequest;
import com.irvin.inventory.user.dto.CreateUserRequest;
import com.irvin.inventory.user.dto.UpdateUserRequest;
import com.irvin.inventory.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Lock order: active admins (by id) -> target user -> its refresh tokens.
@Service
@RequiredArgsConstructor
public class UserService {

    // Only the SORTABLE fields can be sorted by: anything else could order by sensitive columns.
    private static final Sort DEFAULT_SORT = Sort.by("name", "id");
    // Orders a client may ask for (API name -> entity path); the default one breaks ties.
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "email", "email",
            "role", "role",
            "createdAt", "createdAt");

    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already registered");
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setPhone(request.phone());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        Pageable sorted = Sorting.apply(pageable, SORTABLE, DEFAULT_SORT);
        return userRepository.findAll(sorted).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(userRepository.findById(id).orElseThrow(UserService::notFound));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        boolean removesAdminAccess = request.role() != Role.ADMIN || !request.active();
        List<User> activeAdmins = removesAdminAccess ? userRepository.lockActiveByRole(Role.ADMIN) : List.of();
        User user = lockUser(id);

        boolean isActiveAdmin = user.getRole() == Role.ADMIN && user.isActive();
        if (removesAdminAccess && isActiveAdmin && activeAdmins.size() <= 1) {
            throw new ConflictException("At least one active admin is required");
        }

        // Role or status changes must not survive in previously issued refresh tokens.
        boolean revokeSessions = user.getRole() != request.role() || (user.isActive() && !request.active());
        user.setName(request.name());
        user.setRole(request.role());
        user.setPhone(request.phone());
        user.setActive(request.active());
        if (revokeSessions) {
            authService.revokeAllSessions(user.getId());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = lockUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authService.revokeAllSessions(userId);
    }

    private User lockUser(Long id) {
        return userRepository.findByIdForUpdate(id).orElseThrow(UserService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("User not found");
    }
}
