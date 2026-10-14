package com.irvin.inventory.report.dto;

import com.irvin.inventory.catalog.Product;

public record LowStockProductResponse(
        Long productId,
        String sku,
        String barcode,
        String name,
        CategoryRef category,
        int stock,
        int minStock,
        // Units needed to get back to minStock (0 when exactly at the minimum).
        int shortage) {

    public record CategoryRef(Long id, String name) {
    }

    public static LowStockProductResponse from(Product product) {
        return new LowStockProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                new CategoryRef(product.getCategory().getId(), product.getCategory().getName()),
                product.getStock(),
                product.getMinStock(),
                product.getMinStock() - product.getStock());
    }
}
