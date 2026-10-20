package com.irvin.inventory.purchasing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Purchase filters; null values are ignored. The date range is [from, to).
// Validated on construction, so a bad range fails before any transaction or query.
public record PurchaseSearch(Long supplierId, Instant from, Instant to) {

    public PurchaseSearch {
        DateBounds.requireRange(from, to, "from", "to");
    }

    Specification<Purchase> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("purchaseDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
