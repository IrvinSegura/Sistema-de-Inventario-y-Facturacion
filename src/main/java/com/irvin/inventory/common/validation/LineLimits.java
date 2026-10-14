package com.irvin.inventory.common.validation;

// Shared bound for purchase, sale and adjustment lines; with prices up to 99,999.99 it keeps
// every line subtotal within NUMERIC(12,2).
public final class LineLimits {

    public static final int MAX_QUANTITY = 100_000;

    private LineLimits() {
    }
}
