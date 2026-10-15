package com.irvin.inventory.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.irvin.inventory.sale.PaymentMethod;

// Money that moved through the checkout on one local day: sales count on the day they were rung up,
// cancellations on the day they were made. Once "closed", later cancellations land on their own day,
// so the figures no longer change.
public record CashClosingResponse(
        LocalDate date,
        String timeZone,
        // Null for the whole store.
        Long sellerId,
        // False until the day is over plus a margin for sales still being recorded at midnight.
        boolean closed,
        // Every payment method, including those without movements, in a fixed order.
        Map<PaymentMethod, Amounts> byPaymentMethod,
        Amounts total,
        // Cash that should be in the drawer, on top of the opening float. Can be negative when
        // cancellations exceed sales that day.
        BigDecimal expectedCash) {

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
}
