package com.irvin.inventory.sale;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Cash drawer filters; null values are ignored. Business dates are inclusive local days.
// Validated on construction, so a bad range fails before any transaction or query.
public record CashDrawerSearch(Long userId, LocalDate from, LocalDate to) {

    public CashDrawerSearch {
        DateBounds.requireWithin(from, to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
    }

    // Sellers only see their own drawers, whatever user filter they sent.
    CashDrawerSearch withUser(Long userId) {
        return new CashDrawerSearch(userId, from, to);
    }

    Specification<CashDrawer> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
