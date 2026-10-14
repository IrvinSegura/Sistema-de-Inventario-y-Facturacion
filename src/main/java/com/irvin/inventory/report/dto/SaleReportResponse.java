package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.irvin.inventory.sale.PaymentMethod;

// Amounts count completed sales only; cancelled sales are reported separately.
public record SaleReportResponse(
        LocalDate from,
        LocalDate to,
        String timeZone,
        Totals completed,
        Totals cancelled,
        BigDecimal averageTicket,
        List<PaymentMethodTotal> byPaymentMethod,
        List<SellerTotal> bySeller,
        List<ProductTotal> topProducts,
        // One entry per day in the range, including days without sales.
        List<DailyTotal> byDay) {

    public record Totals(long saleCount, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    public record PaymentMethodTotal(PaymentMethod paymentMethod, long saleCount, BigDecimal total) {
    }

    public record SellerTotal(Long sellerId, String name, long saleCount, BigDecimal total) {
    }

    public record ProductTotal(Long productId, String sku, String name, long quantity, BigDecimal subtotal) {
    }

    public record DailyTotal(LocalDate date, long saleCount, BigDecimal total) {
    }
}
