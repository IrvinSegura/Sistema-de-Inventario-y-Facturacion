package com.irvin.inventory.purchasing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Purchase filters; null values are ignored. [from, to) applies to the purchase date, [cancelledFrom,
// cancelledTo) to the cancellation date (only cancelled purchases match). Validated on construction, so
// a bad range fails before any transaction or query.
public record PurchaseSearch(
        Long supplierId, PurchaseStatus status, Instant from, Instant to, Instant cancelledFrom,
        Instant cancelledTo) {

    public PurchaseSearch {
        DateBounds.requireRange(from, to, "from", "to");
        DateBounds.requireRange(cancelledFrom, cancelledTo, "cancelledFrom", "cancelledTo");
    }

    Specification<Purchase> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("purchaseDate"), to));
            }
            // Uses the partial index ix_purchase_cancelled_at (V29).
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
