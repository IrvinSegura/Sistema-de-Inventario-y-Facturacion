package com.irvin.inventory.purchasing.dto;

import com.irvin.inventory.purchasing.Supplier;

public record SupplierResponse(
        Long id, String name, String taxId, String email, String phone, String address, boolean active, Long version) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getTaxId(), supplier.getEmail(),
                supplier.getPhone(), supplier.getAddress(), supplier.isActive(), supplier.getVersion());
    }
}
