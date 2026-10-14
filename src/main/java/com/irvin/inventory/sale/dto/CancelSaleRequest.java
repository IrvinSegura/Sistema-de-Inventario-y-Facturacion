package com.irvin.inventory.sale.dto;

import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelSaleRequest(@NotBlank @Size(max = 255) String reason) {

    public CancelSaleRequest {
        reason = Normalization.trimToNull(reason);
    }
}
