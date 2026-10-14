package com.irvin.inventory.stock.dto;

import java.time.Instant;

import com.irvin.inventory.stock.MovementType;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        MovementType type,
        // Positive = in, negative = out.
        int quantity,
        int resultingStock,
        // Only for ADJUSTMENT.
        String reason,
        // Source document: purchase for PURCHASE; sale for SALE and SALE_CANCELLATION.
        Long purchaseId,
        Long saleId,
        Long saleNumber,
        Ref recordedBy,
        Instant movementDate) {

    public record Ref(Long id, String name) {
    }
}
