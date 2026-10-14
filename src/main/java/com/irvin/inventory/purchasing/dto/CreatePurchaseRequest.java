package com.irvin.inventory.purchasing.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Totals are computed by the server; the client only sends quantities and unit costs.
public record CreatePurchaseRequest(
        @NotNull Long supplierId,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull Item> items) {

    // Limits keep every subtotal within NUMERIC(12,2).
    public record Item(
            @NotNull Long productId,
            @NotNull @Positive @Max(100_000) Integer quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal unitCost) {
    }
}
