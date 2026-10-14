package com.irvin.inventory.catalog;

import java.util.List;

import com.irvin.inventory.catalog.dto.CategoryResponse;
import com.irvin.inventory.catalog.dto.CreateCategoryRequest;
import com.irvin.inventory.catalog.dto.UpdateCategoryRequest;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // Few rows, so no pagination.
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(categoryRepository.findById(id).orElseThrow(CategoryService::notFound));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.nameExists(request.name())) {
            throw duplicateName();
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setDefaultTaxRate(request.defaultTaxRate());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findByIdForUpdate(id).orElseThrow(CategoryService::notFound);
        if (!category.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        if (categoryRepository.nameExistsForOtherId(request.name(), id)) {
            throw duplicateName();
        }
        if (category.isActive() && !request.active() && productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new ConflictException("Category has active products; deactivate or move them first");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        category.setDefaultTaxRate(request.defaultTaxRate());
        category.setActive(request.active());
        // Flush so the response carries the incremented version.
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Category not found");
    }

    private static ConflictException duplicateName() {
        return new ConflictException("Category name already exists");
    }
}
