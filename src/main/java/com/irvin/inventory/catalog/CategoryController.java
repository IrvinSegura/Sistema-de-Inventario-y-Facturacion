package com.irvin.inventory.catalog;

import java.net.URI;
import java.util.List;

import com.irvin.inventory.catalog.dto.CategoryResponse;
import com.irvin.inventory.catalog.dto.CreateCategoryRequest;
import com.irvin.inventory.catalog.dto.UpdateCategoryRequest;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Categories", description = "Read: any user. Write: ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List categories sorted by name")
    @GetMapping
    public List<CategoryResponse> findAll(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.findAll(includeInactive);
    }

    @Operation(summary = "Get a category")
    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    @Operation(summary = "Create a category")
    @PreAuthorize(Roles.MANAGERS)
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id())).body(category);
    }

    @Operation(summary = "Update a category; it can't be deactivated while it has active products")
    @PreAuthorize(Roles.MANAGERS)
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }
}
