package com.irvin.inventory.stock.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.LineLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStockAdjustmentRequest(
        @NotNull Long productId,
        // Signed: positive adds units (e.g. found in a count), negative removes them (e.g. damaged).
        @NotNull @Min(-LineLimits.MAX_QUANTITY) @Max(LineLimits.MAX_QUANTITY) Integer quantity,
        // Required by ck_inventory_movement_type: every manual change must say why.
        @NotBlank @Size(max = 255) String reason) {

    public CreateStockAdjustmentRequest {
        reason = Normalization.trimToNull(reason);
    }
}
