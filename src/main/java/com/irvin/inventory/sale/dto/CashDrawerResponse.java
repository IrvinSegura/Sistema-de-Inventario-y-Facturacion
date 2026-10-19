package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.irvin.inventory.sale.CashDrawer;
import com.irvin.inventory.sale.CashDrawerMember;
import com.irvin.inventory.sale.dto.SaleResponse.Ref;
import com.irvin.inventory.user.User;

public record CashDrawerResponse(
        Long id,
        // Counts and closes it; one of the members.
        Ref responsible,
        // The responsible for a personal drawer, the manager who handed over the float otherwise.
        Ref openedBy,
        // Who sells from it, by name; more than one for a shared drawer.
        List<Ref> members,
        // Local day in the store's time zone.
        LocalDate businessDate,
        BigDecimal openingFloat,
        Instant openedAt,
        // Null while the drawer is open.
        Closing closing) {

    // Snapshot taken when the drawer was closed; later cancellations don't change it.
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

    // With the drawer's own (initialized) members.
    public static CashDrawerResponse from(CashDrawer drawer) {
        return from(drawer, drawer.getMembers().stream()
                .map(CashDrawerMember::getUser)
                .sorted(Comparator.comparing(User::getName).thenComparing(User::getId))
                .map(CashDrawerResponse::ref)
                .toList());
    }

    public static CashDrawerResponse from(CashDrawer drawer, List<Ref> members) {
        Closing closing = drawer.isOpen() ? null : Closing.of(
                drawer.getClosedAt(),
                ref(drawer.getClosedBy()),
                drawer.getExpectedCash(),
                drawer.getCountedCash(),
                drawer.getClosingNote());
        return new CashDrawerResponse(
                drawer.getId(),
                ref(drawer.getResponsible()),
                ref(drawer.getOpenedBy()),
                members,
                drawer.getBusinessDate(),
                drawer.getOpeningFloat(),
                drawer.getOpenedAt(),
                closing);
    }

    public static Ref ref(User user) {
        return new Ref(user.getId(), user.getName());
    }
}
