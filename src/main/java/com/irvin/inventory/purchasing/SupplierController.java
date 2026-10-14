package com.irvin.inventory.purchasing;

import java.net.URI;

import com.irvin.inventory.purchasing.dto.CreateSupplierRequest;
import com.irvin.inventory.purchasing.dto.SupplierResponse;
import com.irvin.inventory.purchasing.dto.UpdateSupplierRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Suppliers", description = "ADMIN only")
@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "Search suppliers sorted by name")
    @GetMapping
    public Page<SupplierResponse> search(
            @Parameter(description = "Matches name or tax ID") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean active,
            @ParameterObject Pageable pageable) {
        return supplierService.search(new SupplierSearch(search, active), pageable);
    }

    @Operation(summary = "Get a supplier")
    @GetMapping("/{id}")
    public SupplierResponse findById(@PathVariable Long id) {
        return supplierService.findById(id);
    }

    @Operation(summary = "Create a supplier")
    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse supplier = supplierService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + supplier.id())).body(supplier);
    }

    @Operation(summary = "Update a supplier (send the version you loaded)")
    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSupplierRequest request) {
        return supplierService.update(id, request);
    }
}
