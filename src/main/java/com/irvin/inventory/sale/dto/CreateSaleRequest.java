package com.irvin.inventory.sale.dto;

import java.util.List;

import com.irvin.inventory.sale.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Prices and VAT rates come from the catalog and totals are computed server-side;
// the client only sends products and quantities.
public record CreateSaleRequest(
        // Optional: without it the sale goes to the walk-in customer (PUBLICO EN GENERAL).
        Long customerId,
        @NotNull PaymentMethod paymentMethod,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull Item> items) {

    public record Item(
            @NotNull Long productId,
            @NotNull @Positive @Max(100_000) Integer quantity) {
    }
}
