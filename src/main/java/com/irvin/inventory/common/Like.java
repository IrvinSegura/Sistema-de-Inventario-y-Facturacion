package com.irvin.inventory.common;

import java.util.Locale;

// Builds case-insensitive "contains" patterns for SQL LIKE with user input.
public final class Like {

    public static final char ESCAPE = '\\';

    private Like() {
    }

    // User input like "50%" must match literally, not as a wildcard.
    public static String containsIgnoreCase(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
