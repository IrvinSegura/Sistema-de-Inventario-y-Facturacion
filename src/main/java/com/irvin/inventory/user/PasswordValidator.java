package com.irvin.inventory.user;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    private static final int MIN_LENGTH = 8;
    // BCrypt rejects inputs over 72 bytes; multi-byte characters (ñ, accents) count double.
    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotBlank reports missing values
        }
        return value.length() >= MIN_LENGTH && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
