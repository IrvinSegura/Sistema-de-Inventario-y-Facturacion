package com.irvin.inventory.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.irvin.inventory.purchasing.Purchase;
import com.irvin.inventory.purchasing.PurchaseStatus;
import com.irvin.inventory.purchasing.dto.PurchaseResponse.Cancellation;
import com.irvin.inventory.purchasing.dto.PurchaseResponse.Ref;

// List view without items; GET /purchases/{id} returns the full detail.
public record PurchaseSummaryResponse(
        Long id,
        Ref supplier,
        Ref recordedBy,
        Instant purchaseDate,
        PurchaseStatus status,
        BigDecimal total,
        // Null unless cancelled; inlined so the list shows who cancelled it and why without detail calls.
        Cancellation cancellation) {

    public static PurchaseSummaryResponse from(Purchase purchase) {
        return new PurchaseSummaryResponse(
                purchase.getId(),
                new Ref(purchase.getSupplier().getId(), purchase.getSupplier().getName()),
                new Ref(purchase.getUser().getId(), purchase.getUser().getName()),
                purchase.getPurchaseDate(),
                purchase.getStatus(),
                purchase.getTotal(),
                Cancellation.from(purchase));
    }
}
