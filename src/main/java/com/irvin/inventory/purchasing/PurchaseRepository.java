package com.irvin.inventory.purchasing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    // List view: header only, no items.
    @Override
    @EntityGraph(attributePaths = {"supplier", "user"})
    Page<Purchase> findAll(Specification<Purchase> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "user", "items", "items.product"})
    Optional<Purchase> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"supplier", "user", "items", "items.product"})
    Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey);
}
