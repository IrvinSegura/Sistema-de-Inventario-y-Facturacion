package com.irvin.inventory.catalog;

import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.Like;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

// Product filters: active always applies; blank text, null categoryId and lowStock=false are ignored.
public record ProductSearch(String text, Long categoryId, boolean active, boolean lowStock) {

    Specification<Product> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), active));
            if (text != null && !text.isBlank()) {
                String pattern = Like.containsIgnoreCase(text);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(root.get("sku")), pattern, Like.ESCAPE)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (lowStock) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stock"), root.<Integer>get("minStock")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
