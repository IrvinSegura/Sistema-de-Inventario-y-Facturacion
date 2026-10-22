package com.irvin.inventory.purchasing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    // List view: header only, no items.
    @Override
    @EntityGraph(attributePaths = {"supplier", "user", "cancelledBy"})
    Page<Purchase> findAll(Specification<Purchase> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "user", "cancelledBy", "items", "items.product"})
    Optional<Purchase> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"supplier", "user", "cancelledBy", "items", "items.product"})
    Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey);

    // Atomic: of two concurrent cancellations only one matches status = 'COMPLETED'.
    // Clears the persistence context so a later read sees the cancelled row.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE purchase
            SET status = 'CANCELLED', cancelled_at = :cancelledAt, cancelled_by = :userId,
                cancellation_reason = :reason
            WHERE id = :id AND status = 'COMPLETED'""")
    int cancel(Long id, Instant cancelledAt, Long userId, String reason);
}
