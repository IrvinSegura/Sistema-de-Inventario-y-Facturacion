package com.irvin.inventory.catalog.dto;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.Category;

public record CategoryResponse(
        Long id, String name, String description, BigDecimal defaultTaxRate, boolean active, Long version) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getDefaultTaxRate(), category.isActive(), category.getVersion());
    }
}
