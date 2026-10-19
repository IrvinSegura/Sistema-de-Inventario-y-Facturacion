package com.irvin.inventory.customer;

import java.net.URI;

import com.irvin.inventory.customer.dto.CreateCustomerRequest;
import com.irvin.inventory.customer.dto.CustomerResponse;
import com.irvin.inventory.customer.dto.UpdateCustomerRequest;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sellers register customers at the checkout; tax ID changes, deactivation and deletion are for admins and supervisors.
@Tag(name = "Customers", description = "Any user; tax ID, active flag and deletion: ADMIN or SUPERVISOR. "
        + "The walk-in customer (PUBLICO EN GENERAL) is read-only")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    // Invoices (CFDI 4.0) need all three, so they're sent together or not at all.
    private static final String FISCAL_DATA_RULE = "Tax ID, fiscalPostalCode (5 digits) and taxRegime (SAT "
            + "c_RegimenFiscal code that applies to the tax ID: 12 characters company, 13 individual) are sent "
            + "together or not at all (400).";

    private final CustomerService customerService;

    @Operation(summary = "Search customers sorted by name")
    @GetMapping
    public Page<CustomerResponse> search(
            @Parameter(description = "Matches name or tax ID") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean active,
            @ParameterObject Pageable pageable) {
        return customerService.search(new CustomerSearch(search, active), pageable);
    }

    @Operation(summary = "Get a customer")
    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @Operation(summary = "Create a customer", description = FISCAL_DATA_RULE)
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse customer = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
    }

    @Operation(summary = "Update a customer (send the version you loaded)",
            description = "Changing the tax ID or the active flag requires ADMIN or SUPERVISOR (403 for sellers). "
                    + FISCAL_DATA_RULE)
    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return customerService.update(id, request, CurrentUser.from(jwt));
    }

    @Operation(summary = "Delete a customer that has no sales",
            description = "A customer with sales answers 409: deactivate it instead, which keeps its history.")
    @PreAuthorize(Roles.MANAGERS)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
