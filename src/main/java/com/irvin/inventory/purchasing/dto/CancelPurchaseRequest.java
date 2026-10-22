package com.irvin.inventory.purchasing.dto;

import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPurchaseRequest(@NotBlank @Size(max = 255) String reason) {

    public CancelPurchaseRequest {
        reason = Normalization.trimToNull(reason);
    }
}
