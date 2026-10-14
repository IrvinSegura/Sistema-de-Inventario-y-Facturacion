package com.irvin.inventory.stock;

import java.time.Instant;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;

// Stock ledger filters for one product; null type and dates are ignored. The date range is [from, to).
// Validated on construction, so a bad range fails before any transaction or query.
public record InventoryMovementSearch(Long productId, MovementType type, Instant from, Instant to) {

    public InventoryMovementSearch {
        if (DateBounds.isOutside(from) || DateBounds.isOutside(to)) {
            throw new BadRequestException(
                    "Dates must be between " + DateBounds.MIN_INSTANT + " and " + DateBounds.MAX_INSTANT);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
    }
}
