package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A manager opens a drawer for one user, or a shared one for several; the responsible, one of the
// members, counts and closes it. The float has the same cap as a personal drawer's.
public record OpenAssignedCashDrawerRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal openingFloat,
        @NotEmpty @Size(max = 20) List<@NotNull Long> memberIds,
        @NotNull Long responsibleId) {
}
