package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.irvin.inventory.report.dto.LowStockProductResponse.CategoryRef;

// One product's completed sales in a period, with its current catalog data.
public record ProductSalesResponse(
        Long productId,
        String sku,
        String name,
        CategoryRef category,
        boolean active,
        int stock,
        // Current weighted average cost per unit; null until the product is first purchased.
        BigDecimal averageCost,
        long quantity,
        // Before VAT.
        BigDecimal subtotal,
        // Cost of the units sold and subtotal minus it; null when any of them has no recorded cost.
        BigDecimal cost,
        BigDecimal grossProfit,
        // Its latest completed sale ever, not only in the period; null if it never sold.
        Instant lastSaleDate) {
}
