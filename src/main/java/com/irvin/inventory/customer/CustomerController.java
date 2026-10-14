package com.irvin.inventory.customer;

import java.net.URI;

import com.irvin.inventory.customer.dto.CreateCustomerRequest;
import com.irvin.inventory.customer.dto.CustomerResponse;
import com.irvin.inventory.customer.dto.UpdateCustomerRequest;
import com.irvin.inventory.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sellers register customers at the checkout; tax ID changes and deactivation are ADMIN only.
@Tag(name = "Customers", description = "Any user; tax ID and active flag: ADMIN only. "
        + "The walk-in customer (PUBLICO EN GENERAL) is read-only")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

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

    @Operation(summary = "Create a customer")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse customer = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
    }

    @Operation(summary = "Update a customer (send the version you loaded)",
            description = "Changing the tax ID or the active flag requires ADMIN (403 for sellers).")
    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return customerService.update(id, request, CurrentUser.from(jwt));
    }
}
