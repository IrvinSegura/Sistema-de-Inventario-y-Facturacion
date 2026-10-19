package com.irvin.inventory.catalog;

import java.util.Objects;

import com.irvin.inventory.catalog.dto.CreateProductRequest;
import com.irvin.inventory.catalog.dto.ProductResponse;
import com.irvin.inventory.catalog.dto.UpdateProductRequest;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.common.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Sort DEFAULT_SORT = Sort.by("name", "id");
    static final String IN_USE_MESSAGE = "Product has stock movements; deactivate it instead";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectProvider<ImageStorage> imageStorage;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(ProductSearch search, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return productRepository.findAll(search.toSpecification(), sorted).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(productRepository.findWithCategoryById(id).orElseThrow(ProductService::notFound));
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        return ProductResponse.from(
                productRepository.findByBarcodeAndActiveTrue(barcode).orElseThrow(ProductService::notFound));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw duplicate("SKU");
        }
        if (request.barcode() != null && productRepository.existsByBarcode(request.barcode())) {
            throw duplicate("Barcode");
        }
        if (productRepository.existsByNameIgnoreCase(request.name())) {
            throw duplicate("Name");
        }
        Category category = lockActiveCategory(request.categoryId());

        Product product = new Product();
        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setCategory(category);
        product.setSalePrice(request.salePrice());
        product.setTaxRate(Objects.requireNonNullElse(request.taxRate(), category.getDefaultTaxRate()));
        product.setMinStock(request.minStock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        // Category before product: matches the catalog lock order, and loading the product first
        // would leave a managed category whose "active" flag predates the lock.
        Category category = request.active()
                ? lockActiveCategory(request.categoryId())
                : categoryRepository.findById(request.categoryId()).orElseThrow(ProductService::categoryNotFound);
        Product product = productRepository.findWithCategoryById(id).orElseThrow(ProductService::notFound);
        if (!product.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw duplicate("SKU");
        }
        if (request.barcode() != null && productRepository.existsByBarcodeAndIdNot(request.barcode(), id)) {
            throw duplicate("Barcode");
        }
        if (productRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw duplicate("Name");
        }

        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setCategory(category);
        product.setSalePrice(request.salePrice());
        product.setTaxRate(request.taxRate());
        product.setMinStock(request.minStock());
        product.setActive(request.active());
        // Flush so the response carries the incremented version.
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    // Only products never purchased, sold or adjusted: deleting one with movements would erase its
    // history (the foreign keys also block it if a sale races this delete). The photo goes after the commit.
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id).orElseThrow(ProductService::notFound);
        if (productRepository.hasMovements(id)) {
            throw new ConflictException(IN_USE_MESSAGE);
        }
        String imageUrl = product.getImageUrl();
        productRepository.delete(product);
        productRepository.flush();
        if (imageUrl != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    imageStorage.ifAvailable(storage -> storage.delete(imageUrl));
                }
            });
        }
    }

    // Active products must belong to an active category (see CategoryService deactivation rule).
    private Category lockActiveCategory(Long categoryId) {
        Category category = categoryRepository.findByIdForShare(categoryId)
                .orElseThrow(ProductService::categoryNotFound);
        if (!category.isActive()) {
            throw new BadRequestException("Category is inactive");
        }
        return category;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Product not found");
    }

    // 400, not 404: the URL is valid, the referenced category in the body is not.
    private static BadRequestException categoryNotFound() {
        return new BadRequestException("Category not found");
    }

    private static ConflictException duplicate(String field) {
        return new ConflictException(field + " already exists");
    }
}
