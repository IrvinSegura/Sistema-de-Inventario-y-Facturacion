package com.irvin.inventory.catalog;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

// Lock order across the catalog: category -> product.
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();

    List<Category> findByActiveTrueOrderByNameAsc();

    // lower() matches the uk_category_name index on LOWER(name).
    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name)")
    boolean nameExists(String name);

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameExistsForOtherId(String name, Long id);

    // Taken when deactivating, so no product can be activated in the category meanwhile.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForUpdate(Long id);

    // Taken when activating or assigning a product, so the category can't be deactivated meanwhile.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForShare(Long id);
}
