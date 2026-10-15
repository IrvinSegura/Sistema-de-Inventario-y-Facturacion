package com.irvin.inventory.stock;

import java.time.Instant;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;

// Stock ledger filters for one product; null type and dates are ignored. The date range is [from, to).
// Validated on construction, so a bad range fails before any transaction or query.
public record InventoryMovementSearch(Long productId, MovementType type, Instant from, Instant to) {

    public InventoryMovementSearch {
        DateBounds.requireWithin(from, to);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
    }
}
