package com.irvin.inventory.catalog;

import java.net.URI;

import com.irvin.inventory.catalog.dto.CreateProductRequest;
import com.irvin.inventory.catalog.dto.ProductResponse;
import com.irvin.inventory.catalog.dto.UpdateProductRequest;
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

@Tag(name = "Products", description = "Read: any user. Write: ADMIN only")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Search products sorted by name")
    @GetMapping
    public Page<ProductResponse> search(
            @Parameter(description = "Matches name or SKU") @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Only products with stock <= minStock")
            @RequestParam(defaultValue = "false") boolean lowStock,
            @ParameterObject Pageable pageable) {
        return productService.search(new ProductSearch(search, categoryId, active, lowStock), pageable);
    }

    @Operation(summary = "Get a product")
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @Operation(summary = "Find a product by barcode (checkout scan)")
    @GetMapping("/barcode/{barcode}")
    public ProductResponse findByBarcode(@PathVariable String barcode) {
        return productService.findByBarcode(barcode);
    }

    @Operation(summary = "Create a product; tax rate defaults to the category's; stock starts at 0")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @Operation(summary = "Update a product (send the version you loaded); stock is not editable here")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }
}
