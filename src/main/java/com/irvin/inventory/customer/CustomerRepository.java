package com.irvin.inventory.customer;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, Long id);

    // Taken when recording a sale, so the customer can't be deactivated meanwhile.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForShare(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Customer c where c.taxId = :taxId")
    Optional<Customer> findByTaxIdForShare(String taxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(Long id);

    // Sales keep their customer, so one with sales can only be deactivated.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM sale WHERE customer_id = :customerId)", nativeQuery = true)
    boolean hasSales(Long customerId);
}
