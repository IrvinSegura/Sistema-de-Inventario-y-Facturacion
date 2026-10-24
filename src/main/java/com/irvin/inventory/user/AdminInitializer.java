package com.irvin.inventory.user;

import java.util.Set;
import java.util.stream.Collectors;

import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.user.dto.CreateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Creates the first admin from env vars; credentials never live in migrations.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(adminProperties.email()) || !StringUtils.hasText(adminProperties.password())) {
            log.warn("No admin user exists. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one on startup.");
            return;
        }

        CreateUserRequest request = new CreateUserRequest(
                adminProperties.name(), adminProperties.email(), adminProperties.password(), Role.ADMIN, null);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid initial admin configuration: " + details);
        }

        try {
            userService.create(request, null);
            log.info("Initial admin user created.");
        } catch (DataIntegrityViolationException | ConflictException e) {
            // Another instance starting at the same time created it first.
            log.info("Initial admin user already created by another instance.");
        }
    }
}
