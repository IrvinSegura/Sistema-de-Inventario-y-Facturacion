package com.irvin.inventory.customer;

import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.Like;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Customer filters: active always applies; blank text is ignored.
public record CustomerSearch(String text, boolean active) {

    Specification<Customer> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), active));
            if (text != null && !text.isBlank()) {
                String pattern = Like.containsIgnoreCase(text);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("taxId")), pattern, Like.ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
