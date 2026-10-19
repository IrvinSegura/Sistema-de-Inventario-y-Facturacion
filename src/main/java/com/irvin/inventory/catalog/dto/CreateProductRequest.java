package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.TaxRate;
import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.TextRules;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = TextRules.CODE_PATTERN, message = TextRules.CODE_MESSAGE) String sku,
        @Pattern(regexp = ProductRules.BARCODE_PATTERN, message = ProductRules.BARCODE_MESSAGE) String barcode,
        @NotBlank @Size(max = 150) @Pattern(regexp = TextRules.LABEL_PATTERN, message = TextRules.LABEL_MESSAGE)
        String name,
        @Size(max = 500) String description,
        @Size(max = 500) @Pattern(regexp = ProductRules.IMAGE_URL_PATTERN, message = ProductRules.IMAGE_URL_MESSAGE)
        String imageUrl,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        // Optional: defaults to the category's defaultTaxRate.
        @TaxRate BigDecimal taxRate,
        @NotNull @PositiveOrZero Integer minStock) {

    public CreateProductRequest {
        sku = Normalization.code(sku);
        barcode = Normalization.trimToNull(barcode);
        name = Normalization.trimToNull(name);
        description = Normalization.trimToNull(description);
        imageUrl = Normalization.trimToNull(imageUrl);
    }
}
