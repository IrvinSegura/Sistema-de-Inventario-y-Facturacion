package com.irvin.inventory.purchasing;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, Long id);

    // Taken when recording a purchase, so the supplier can't be deactivated meanwhile.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> findByIdForShare(Long id);
}
