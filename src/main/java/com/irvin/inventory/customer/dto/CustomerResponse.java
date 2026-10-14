package com.irvin.inventory.customer.dto;

import com.irvin.inventory.customer.Customer;

public record CustomerResponse(
        Long id, String name, String taxId, String email, String phone, String address, boolean active,
        boolean walkIn, Long version) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getTaxId(), customer.getEmail(),
                customer.getPhone(), customer.getAddress(), customer.isActive(), customer.isWalkIn(),
                customer.getVersion());
    }
}
