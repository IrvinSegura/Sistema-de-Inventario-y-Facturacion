package com.irvin.inventory.sale;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Closing difference (shortage or surplus), in pesos, from which clients treat a closed drawer as
// unbalanced; the cash closing returns it to managers. Positive: zero would flag every balanced drawer.
@Validated
@ConfigurationProperties("app.cash-drawer")
public record CashDrawerProperties(
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal unbalancedThreshold) {

    // Money goes out with two decimals, so "50" from the environment reads 50.00.
    public CashDrawerProperties {
        if (unbalancedThreshold != null && unbalancedThreshold.scale() <= 2) {
            unbalancedThreshold = unbalancedThreshold.setScale(2);
        }
    }
}
