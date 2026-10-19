package com.irvin.inventory.catalog;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

// Category is fetched eagerly in reads that return it, avoiding N+1 queries.
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    // Checkout scan: inactive products are not sellable.
    @EntityGraph(attributePaths = "category")
    Optional<Product> findByBarcodeAndActiveTrue(String barcode);

    // Reorder list: active products at or below their minimum. Out of stock first (they can't
    // be sold at all), then furthest below the minimum.
    @Query(value = """
            select p from Product p join fetch p.category
            where p.active = true and p.stock <= p.minStock
              and (:categoryId is null or p.category.id = :categoryId)
            order by case when p.stock = 0 then 0 else 1 end, (p.stock - p.minStock), p.name, p.id""",
            countQuery = """
            select count(p) from Product p
            where p.active = true and p.stock <= p.minStock
              and (:categoryId is null or p.category.id = :categoryId)""")
    Page<Product> findLowStock(Long categoryId, Pageable pageable);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    // Case-insensitive, like the uk_product_name index (V12) that backs it.
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Every purchase, sale and adjustment writes a movement, so none means the product was never used.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM inventory_movement WHERE product_id = :productId)", nativeQuery = true)
    boolean hasMovements(Long productId);
}
