package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.irvin.inventory.catalog.Product;

public record ProductResponse(
        Long id,
        String sku,
        String barcode,
        String name,
        String description,
        String imageUrl,
        CategoryRef category,
        BigDecimal salePrice,
        BigDecimal taxRate,
        int stock,
        int minStock,
        boolean lowStock,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public record CategoryRef(Long id, String name) {
    }

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                new CategoryRef(product.getCategory().getId(), product.getCategory().getName()),
                product.getSalePrice(),
                product.getTaxRate(),
                product.getStock(),
                product.getMinStock(),
                product.isLowStock(),
                product.isActive(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
