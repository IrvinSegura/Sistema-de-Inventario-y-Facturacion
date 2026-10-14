package com.irvin.inventory.sale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Sale filters; null values are ignored. The date range is [from, to).
public record SaleSearch(
        Long saleNumber, Long customerId, Long sellerId, SaleStatus status, Instant from, Instant to) {

    // Sellers only see their own sales, whatever seller filter they sent.
    SaleSearch withSeller(Long sellerId) {
        return new SaleSearch(saleNumber, customerId, sellerId, status, from, to);
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
