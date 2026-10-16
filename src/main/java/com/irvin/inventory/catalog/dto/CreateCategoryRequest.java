package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.TaxRate;
import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.TextRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = TextRules.LABEL_PATTERN, message = TextRules.LABEL_MESSAGE)
        String name,
        @Size(max = 255) String description,
        @NotNull @TaxRate BigDecimal defaultTaxRate) {

    public CreateCategoryRequest {
        name = Normalization.trimToNull(name);
        description = Normalization.trimToNull(description);
    }
}
