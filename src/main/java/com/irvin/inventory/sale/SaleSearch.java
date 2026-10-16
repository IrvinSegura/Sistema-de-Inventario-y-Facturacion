package com.irvin.inventory.sale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Sale filters; null values are ignored. The date range is [from, to); cashDrawerId matches the
// drawer the sale was recorded in.
// Validated on construction, so a bad range fails before any transaction or query.
public record SaleSearch(
        Long saleNumber, Long customerId, Long sellerId, Long cashDrawerId, SaleStatus status, Instant from,
        Instant to) {

    public SaleSearch {
        DateBounds.requireWithin(from, to);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
    }

    // Sellers only see their own sales, whatever seller filter they sent.
    SaleSearch withSeller(Long sellerId) {
        return new SaleSearch(saleNumber, customerId, sellerId, cashDrawerId, status, from, to);
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
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("saleDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
