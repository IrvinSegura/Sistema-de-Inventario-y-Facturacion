package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

// Cash put in the drawer to give change; zero is allowed. Capped well below NUMERIC(12,2), so the
// expected cash (float + the day's net cash) still fits.
public record OpenCashDrawerRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal openingFloat) {
}
