package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.irvin.inventory.sale.CashDrawer;
import com.irvin.inventory.sale.dto.SaleResponse.Ref;

public record CashDrawerResponse(
        Long id,
        Ref user,
        // Local day in the store's time zone.
        LocalDate businessDate,
        BigDecimal openingFloat,
        Instant openedAt,
        // Null while the drawer is open.
        Closing closing) {

    // Snapshot taken when the drawer was closed; later cancellations of that day don't change it.
    // difference = countedCash - expectedCash: positive is a surplus, negative a shortage.
    public record Closing(
            Instant closedAt,
            Ref closedBy,
            BigDecimal expectedCash,
            BigDecimal countedCash,
            BigDecimal difference,
            String note) {

        public static Closing of(Instant closedAt, Ref closedBy, BigDecimal expectedCash, BigDecimal countedCash,
                String note) {
            return new Closing(closedAt, closedBy, expectedCash, countedCash, countedCash.subtract(expectedCash), note);
        }
    }

    public static CashDrawerResponse from(CashDrawer drawer) {
        Closing closing = drawer.isOpen() ? null : Closing.of(
                drawer.getClosedAt(),
                new Ref(drawer.getClosedBy().getId(), drawer.getClosedBy().getName()),
                drawer.getExpectedCash(),
                drawer.getCountedCash(),
                drawer.getClosingNote());
        return new CashDrawerResponse(
                drawer.getId(),
                new Ref(drawer.getUser().getId(), drawer.getUser().getName()),
                drawer.getBusinessDate(),
                drawer.getOpeningFloat(),
                drawer.getOpenedAt(),
                closing);
    }
}
