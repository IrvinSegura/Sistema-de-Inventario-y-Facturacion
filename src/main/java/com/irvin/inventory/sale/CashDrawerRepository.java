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

// Members aren't fetched with lists of drawers (paging over a collection fetch happens in memory):
// CashDrawerMemberRepository loads them for the whole page at once.
public interface CashDrawerRepository extends JpaRepository<CashDrawer, Long>, JpaSpecificationExecutor<CashDrawer> {

    @Override
    @EntityGraph(attributePaths = {"responsible", "openedBy", "closedBy"})
    Page<CashDrawer> findAll(Specification<CashDrawer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"responsible", "openedBy", "closedBy", "members", "members.user"})
    Optional<CashDrawer> findDetailedById(Long id);

    @Query("select d.responsible.id from CashDrawer d where d.id = :id")
    Optional<Long> findResponsibleIdById(Long id);

    // Taken by every sale and cancellation (shared, so they don't block each other): the drawer can't
    // close while one is being recorded, and no sale gets in after it closed.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from CashDrawer d where d.id = :id")
    Optional<CashDrawer> findByIdForShare(Long id);

    // The cash closing's drawers, in opening order.
    @EntityGraph(attributePaths = {"responsible", "openedBy", "closedBy"})
    List<CashDrawer> findByBusinessDateOrderById(LocalDate businessDate);

    @EntityGraph(attributePaths = {"responsible", "openedBy", "closedBy"})
    @Query("""
            select d from CashDrawer d
            where d.businessDate = :businessDate
              and exists (select 1 from CashDrawerMember m where m.cashDrawer = d and m.user.id = :userId)
            order by d.id""")
    List<CashDrawer> findByBusinessDateAndMember(LocalDate businessDate, Long userId);

    // Taken to close the drawer: waits for the sales and cancellations still holding it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CashDrawer d where d.id = :id")
    Optional<CashDrawer> findByIdForUpdate(Long id);
}
