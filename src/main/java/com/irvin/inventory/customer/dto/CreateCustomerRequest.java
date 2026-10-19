package com.irvin.inventory.customer.dto;

import com.irvin.inventory.common.Normalization;
import com.irvin.inventory.common.validation.Phone;
import com.irvin.inventory.common.validation.TaxId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @TaxId String taxId,
        @Email @Size(max = 150) String email,
        @Phone String phone,
        @Size(max = 255) String address,
        // Fiscal data for invoices: sent together with the tax ID, or none of the three.
        @Pattern(regexp = "^[0-9]{5}$", message = "must be 5 digits") String fiscalPostalCode,
        @Schema(description = "SAT c_RegimenFiscal code, e.g. 601, 605, 612, 616, 626") String taxRegime) {

    public CreateCustomerRequest {
        name = Normalization.trimToNull(name);
        taxId = Normalization.optionalCode(taxId);
        email = Normalization.optionalEmail(email);
        phone = Normalization.trimToNull(phone);
        address = Normalization.trimToNull(address);
        fiscalPostalCode = Normalization.trimToNull(fiscalPostalCode);
        taxRegime = Normalization.trimToNull(taxRegime);
    }
}
