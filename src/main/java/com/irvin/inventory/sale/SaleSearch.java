package com.irvin.inventory.sale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Sale filters; nulls are ignored. [from, to) applies to the sale date, [cancelledFrom, cancelledTo) to
// the cancellation date (only cancelled sales match); cashDrawerId matches the drawer the sale was
// recorded in. Validated on construction, so a bad range fails before any transaction or query.
public record SaleSearch(
        Long saleNumber, Long customerId, Long sellerId, Long cashDrawerId, SaleStatus status, Instant from,
        Instant to, Instant cancelledFrom, Instant cancelledTo) {

    public SaleSearch {
        DateBounds.requireRange(from, to, "from", "to");
        DateBounds.requireRange(cancelledFrom, cancelledTo, "cancelledFrom", "cancelledTo");
    }

    // Only cancelled sales can match, so ordering by cancellation date never meets a null.
    boolean onlyCancelled() {
        return status == SaleStatus.CANCELLED || cancelledFrom != null || cancelledTo != null;
    }

    // Sellers only see their own sales, whatever seller filter they sent.
    SaleSearch withSeller(Long sellerId) {
        return new SaleSearch(saleNumber, customerId, sellerId, cashDrawerId, status, from, to, cancelledFrom,
                cancelledTo);
    }

    Specification<Sale> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (saleNumber != null) {
                predicates.add(cb.equal(root.get("saleNumber"), saleNumber));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (sellerId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), sellerId));
            }
            if (cashDrawerId != null) {
                predicates.add(cb.equal(root.get("cashDrawer").get("id"), cashDrawerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (status == SaleStatus.CANCELLED) {
                // Implied by ck_sale_status, but lets the planner use the partial index ix_sale_cancelled_at.
                predicates.add(cb.isNotNull(root.get("cancelledAt")));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("saleDate"), to));
            }
            // Uses the partial index ix_sale_cancelled_at (V9).
            if (cancelledFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("cancelledAt"), cancelledFrom));
            }
            if (cancelledTo != null) {
                predicates.add(cb.lessThan(root.get("cancelledAt"), cancelledTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
