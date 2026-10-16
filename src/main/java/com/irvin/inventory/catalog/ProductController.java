package com.irvin.inventory.catalog;

import java.net.URI;

import com.irvin.inventory.catalog.dto.CreateProductRequest;
import com.irvin.inventory.catalog.dto.ProductResponse;
import com.irvin.inventory.catalog.dto.UpdateProductRequest;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Products", description = "Read: any user. Write: ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    @Operation(summary = "Search products sorted by name")
    @GetMapping
    public Page<ProductResponse> search(
            @Parameter(description = "Matches name or SKU (contains), or barcode (exact)") @RequestParam(required = false) String search,
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
    @PreAuthorize(Roles.MANAGERS)
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @Operation(summary = "Update a product (send the version you loaded); stock is not editable here")
    @PreAuthorize(Roles.MANAGERS)
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @Operation(summary = "Delete a product that was never purchased, sold or adjusted",
            description = "A product with stock movements answers 409: deactivate it instead, which keeps its history.")
    @PreAuthorize(Roles.MANAGERS)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload or replace a product's image (PNG, JPEG, WebP or GIF, up to 5 MB)",
            description = "The format is checked from the file's content. Changes the product's version.")
    @PreAuthorize(Roles.MANAGERS)
    @PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse replaceImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return productImageService.replaceImage(id, file);
    }

    @Operation(summary = "Remove a product's image", description = "Changes the product's version.")
    @PreAuthorize(Roles.MANAGERS)
    @DeleteMapping("/{id}/image")
    public ProductResponse removeImage(@PathVariable Long id) {
        return productImageService.removeImage(id);
    }
}
