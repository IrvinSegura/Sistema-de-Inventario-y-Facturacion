package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

// One registered customer's completed sales in a period.
public record CustomerSalesResponse(
        Long customerId,
        String name,
        String taxId,
        long saleCount,
        BigDecimal total,
        BigDecimal averageTicket,
        // Their latest completed sale in the period.
        Instant lastSaleDate) {
}
