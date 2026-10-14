package com.irvin.inventory.customer.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.Phone;
import com.irvin.inventory.common.validation.TaxId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @TaxId String taxId,
        @Email @Size(max = 150) String email,
        @Phone String phone,
        @Size(max = 255) String address) {

    public CreateCustomerRequest {
        name = Normalization.trimToNull(name);
        taxId = Normalization.optionalCode(taxId);
        email = Normalization.optionalEmail(email);
        phone = Normalization.trimToNull(phone);
        address = Normalization.trimToNull(address);
    }
}
