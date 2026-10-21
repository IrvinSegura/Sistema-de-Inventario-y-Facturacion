package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.dto.CashDrawerResponse;
import com.irvin.inventory.sale.dto.SaleResponse.Ref;

// Money that moved through the checkout. For a day: sales count on the day they were rung up,
// cancellations on the day they were made; once "closed", later cancellations land on their own day,
// so the figures no longer change. For one drawer: the sales recorded in it and the refunds that came
// out of it, which no longer change once it is closed.
public record CashClosingResponse(
        // The day, or the drawer's business day.
        LocalDate date,
        String timeZone,
        // Null for the whole store and for a drawer.
        Long sellerId,
        // Null for a day.
        Long cashDrawerId,
        // A day: false until it is over plus a margin for sales still being recorded at midnight.
        // A drawer: false until it is closed.
        boolean closed,
        // Every payment method, including those without movements, in a fixed order.
        Map<PaymentMethod, Amounts> byPaymentMethod,
        Amounts total,
        // The same movements by seller of the sale, by name; a shared drawer has one per member who sold.
        List<SellerAmounts> bySeller,
        // Sum of the opening floats of the drawers below.
        BigDecimal openingFloat,
        // Cash that should be in the drawers now: openingFloat + net cash. For a day, unlike a drawer's
        // closing snapshot, it includes cancellations made after the drawer was closed.
        BigDecimal expectedCash,
        // The day's drawers in opening order (with a seller, the ones they're in), or the drawer.
        List<CashDrawerResponse> drawers,
        // A closed drawer whose difference reaches this, either way, is unbalanced
        // (app.cash-drawer.unbalanced-threshold); null for sellers.
        BigDecimal unbalancedThreshold) {

    // net = salesTotal - cancellationsTotal; can be negative.
    public record Amounts(
            long saleCount,
            BigDecimal salesTotal,
            long cancellationCount,
            BigDecimal cancellationsTotal,
            BigDecimal net) {

        public static final Amounts NONE = of(0, BigDecimal.ZERO.setScale(2), 0, BigDecimal.ZERO.setScale(2));

        public static Amounts of(long saleCount, BigDecimal salesTotal, long cancellationCount,
                BigDecimal cancellationsTotal) {
            return new Amounts(saleCount, salesTotal, cancellationCount, cancellationsTotal,
                    salesTotal.subtract(cancellationsTotal));
        }

        public Amounts plus(Amounts other) {
            return of(saleCount + other.saleCount, salesTotal.add(other.salesTotal),
                    cancellationCount + other.cancellationCount, cancellationsTotal.add(other.cancellationsTotal));
        }
    }

    // One seller's cash (their share of the drawer) and all their payment methods.
    public record SellerAmounts(Ref seller, Amounts cash, Amounts total) {
    }
}
