package com.irvin.inventory.stock;

import java.time.Duration;
import java.time.Instant;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;

// Stock ledger filters: one product's ledger (type and dates optional), or one type across the store
// over a period of up to MAX_STORE_WIDE_DAYS. The date range is [from, to).
// Validated on construction, so a bad search fails before any transaction or query.
public record InventoryMovementSearch(Long productId, MovementType type, Instant from, Instant to) {

    // Like the reports' periods: bounds the rows a store-wide page and its count read.
    static final int MAX_STORE_WIDE_DAYS = 366;

    public InventoryMovementSearch {
        DateBounds.requireRange(from, to, "from", "to");
        if (productId == null) {
            if (type == null || from == null || to == null) {
                throw new BadRequestException("Send a productId, or a type with 'from' and 'to'");
            }
            if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_STORE_WIDE_DAYS)) > 0) {
                throw new BadRequestException("A store-wide search can't exceed " + MAX_STORE_WIDE_DAYS + " days");
            }
        }
    }
}
