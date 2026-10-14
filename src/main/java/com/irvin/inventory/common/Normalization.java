package com.irvin.inventory.common;

import java.util.Locale;

// Canonical forms applied in request DTOs so uniqueness checks don't depend on user input format.
public final class Normalization {

    private Normalization() {
    }

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
