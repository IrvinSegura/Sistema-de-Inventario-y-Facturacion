package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Money spent on suppliers in a period: totals, suppliers, the products that cost the most and a
// daily breakdown.
public record PurchaseReportResponse(
        LocalDate from,
        LocalDate to,
        String timeZone,
        // Null for every supplier.
        Long supplierId,
        long purchaseCount,
        long units,
        BigDecimal total,
        // Most money first.
        List<SupplierTotal> bySupplier,
        // Top 10 by money spent.
        List<ProductTotal> topProducts,
        // One entry per day in the range, including days without purchases.
        List<DailyTotal> byDay) {

    public record SupplierTotal(Long supplierId, String name, long purchaseCount, BigDecimal total) {
    }

    public record ProductTotal(Long productId, String sku, String name, long quantity, BigDecimal subtotal,
            // subtotal / quantity, rounded to cents.
            BigDecimal averageUnitCost) {
    }

    public record DailyTotal(LocalDate date, long purchaseCount, BigDecimal total) {
    }
}
