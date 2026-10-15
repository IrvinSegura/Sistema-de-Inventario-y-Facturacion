package com.irvin.inventory.sale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CashDrawerRepository extends JpaRepository<CashDrawer, Long>, JpaSpecificationExecutor<CashDrawer> {

    @Override
    @EntityGraph(attributePaths = {"user", "closedBy"})
    Page<CashDrawer> findAll(Specification<CashDrawer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "closedBy"})
    Optional<CashDrawer> findDetailedById(Long id);

    @EntityGraph(attributePaths = "user")
    Optional<CashDrawer> findByUserIdAndClosedAtIsNull(Long userId);

    @Query("select d.user.id from CashDrawer d where d.id = :id")
    Optional<Long> findUserIdById(Long id);

    boolean existsByUserIdAndBusinessDate(Long userId, LocalDate businessDate);

    // Taken by every sale and cancellation (shared, so they don't block each other): the drawer can't
    // close while one is being recorded, and no sale gets in after it closed.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from CashDrawer d where d.user.id = :userId and d.businessDate = :businessDate")
    Optional<CashDrawer> findByUserIdAndBusinessDateForShare(Long userId, LocalDate businessDate);

    // The cash closing's drawers, by user.
    @EntityGraph(attributePaths = {"user", "closedBy"})
    List<CashDrawer> findByBusinessDateOrderByUserId(LocalDate businessDate);

    @EntityGraph(attributePaths = {"user", "closedBy"})
    List<CashDrawer> findByBusinessDateAndUserId(LocalDate businessDate, Long userId);

    // Taken to close the drawer: waits for the sales and cancellations still holding it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CashDrawer d where d.id = :id")
    Optional<CashDrawer> findByIdForUpdate(Long id);
}
