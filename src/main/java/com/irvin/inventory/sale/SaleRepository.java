package com.irvin.inventory.sale;

import java.math.BigDecimal;
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

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    // List view: header and cancellation, no items.
    @Override
    @EntityGraph(attributePaths = {"customer", "user", "cancelledBy"})
    Page<Sale> findAll(Specification<Sale> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "user", "cancelledBy", "items", "items.product"})
    Optional<Sale> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"customer", "user", "cancelledBy", "items", "items.product"})
    Optional<Sale> findByIdempotencyKey(UUID idempotencyKey);

    @Query("select s.user.id from Sale s where s.id = :id")
    Optional<Long> findSellerIdById(Long id);

    // Atomic: of two concurrent cancellations only one matches status = 'COMPLETED'.
    // Clears the persistence context so a later read sees the cancelled row.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE sale
            SET status = 'CANCELLED', cancelled_at = :cancelledAt, cancelled_by = :userId,
                cancellation_reason = :reason
            WHERE id = :id AND status = 'COMPLETED'""")
    int cancel(Long id, Instant cancelledAt, Long userId, String reason);

    // Separate from cancel: a nullable native parameter has no type PostgreSQL can infer.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE sale SET cancellation_drawer_id = :drawerId WHERE id = :id")
    int chargeCancellation(Long id, Long drawerId);

    // Net cash of a drawer, with the drawer cash closing's rule (SaleReportRepository): cash sales
    // recorded in it, cancelled later or not, minus the cash refunds that came out of it.
    @Query(nativeQuery = true, value = """
            SELECT (SELECT coalesce(sum(total), 0) FROM sale
                    WHERE cash_drawer_id = :drawerId AND payment_method = 'CASH')
                 - (SELECT coalesce(sum(total), 0) FROM sale
                    WHERE cancellation_drawer_id = :drawerId AND payment_method = 'CASH')""")
    BigDecimal drawerNetCash(Long drawerId);
}
