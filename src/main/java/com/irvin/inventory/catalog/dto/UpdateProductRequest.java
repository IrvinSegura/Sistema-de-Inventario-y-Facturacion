package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.TaxRate;
import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Stock is not editable here: it only changes through purchases, sales and adjustments.
public record UpdateProductRequest(
        @NotBlank @Size(max = 50) String sku,
        @Pattern(regexp = ProductRules.BARCODE_PATTERN, message = ProductRules.BARCODE_MESSAGE) String barcode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Size(max = 500) @Pattern(regexp = ProductRules.IMAGE_URL_PATTERN, message = ProductRules.IMAGE_URL_MESSAGE)
        String imageUrl,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        @NotNull @TaxRate BigDecimal taxRate,
        @NotNull @PositiveOrZero Integer minStock,
        @NotNull Boolean active,
        // Version the client loaded; a mismatch means someone else edited the product.
        @NotNull Long version) {

    public UpdateProductRequest {
        sku = Normalization.code(sku);
        barcode = Normalization.trimToNull(barcode);
        name = Normalization.trimToNull(name);
        description = Normalization.trimToNull(description);
        imageUrl = Normalization.trimToNull(imageUrl);
    }
}
