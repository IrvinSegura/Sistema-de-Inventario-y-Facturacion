package com.irvin.inventory.sale.dto;

import java.util.List;

import com.irvin.inventory.common.validation.LineLimits;
import com.irvin.inventory.sale.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
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

    // Explicit name: springdoc names nested records by simple name, and four of them are "Item".
    @Schema(name = "CreateSaleItem")
    public record Item(
            @NotNull Long productId,
            @NotNull @Positive @Max(LineLimits.MAX_QUANTITY) Integer quantity) {
    }
}
