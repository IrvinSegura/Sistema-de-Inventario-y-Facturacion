package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.TaxRate;
import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull @TaxRate BigDecimal defaultTaxRate,
        @NotNull Boolean active,
        // Version the client loaded; a mismatch means someone else edited the category.
        @NotNull Long version) {

    public UpdateCategoryRequest {
        name = Normalization.trimToNull(name);
        description = Normalization.trimToNull(description);
    }
}
