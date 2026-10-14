package com.irvin.inventory.common;

import java.util.Locale;

// Canonical forms applied in request DTOs so uniqueness checks don't depend on user input format.
public final class Normalization {

    private Normalization() {
    }

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // Codes such as SKUs: trimmed and uppercase.
    public static String code(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    // Optional codes such as a tax ID: blank becomes null, otherwise trimmed and uppercase.
    public static String optionalCode(String code) {
        String trimmed = trimToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    // Optional email: blank becomes null, otherwise trimmed and lowercase.
    public static String optionalEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    // Optional text: blank becomes null so "" and null mean the same.
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
