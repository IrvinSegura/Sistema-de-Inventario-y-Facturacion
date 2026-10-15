package com.irvin.inventory.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    // List view: header only, no items.
    @Override
    @EntityGraph(attributePaths = {"customer", "user"})
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

    // Net cash of a user's drawer for one local day, with the cash closing's rule (SaleReportRepository):
    // cash sales rung up that day minus cancellations made that day of their cash sales, whoever made
    // them. PostgreSQL computes the day bounds, as the cash closing does, so both always agree.
    @Query(nativeQuery = true, value = """
            SELECT (SELECT coalesce(sum(total), 0) FROM sale
                    WHERE user_id = :userId AND payment_method = 'CASH'
                      AND sale_date >= CAST(:day AS timestamp) AT TIME ZONE :zone
                      AND sale_date < CAST(:nextDay AS timestamp) AT TIME ZONE :zone)
                 - (SELECT coalesce(sum(total), 0) FROM sale
                    WHERE user_id = :userId AND payment_method = 'CASH'
                      AND cancelled_at >= CAST(:day AS timestamp) AT TIME ZONE :zone
                      AND cancelled_at < CAST(:nextDay AS timestamp) AT TIME ZONE :zone)""")
    BigDecimal netCash(Long userId, LocalDate day, LocalDate nextDay, String zone);
}
