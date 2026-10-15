package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;

import com.irvin.inventory.common.Normalization;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// countedCash is the cash physically in the drawer, opening float included, so its cap is higher
// than the float's: it also holds the day's cash sales.
public record CloseCashDrawerRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) BigDecimal countedCash,
        @Size(max = 255) String note) {

    public CloseCashDrawerRequest {
        note = Normalization.trimToNull(note);
    }
}
