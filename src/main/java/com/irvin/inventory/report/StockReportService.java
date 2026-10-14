package com.irvin.inventory.report;

import com.irvin.inventory.catalog.ProductRepository;
import com.irvin.inventory.report.dto.LowStockProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockReportService {

    private final ProductRepository productRepository;

    // Client sort is dropped: the report's order (most urgent first) is part of its meaning.
    @Transactional(readOnly = true)
    public Page<LowStockProductResponse> lowStock(Long categoryId, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findLowStock(categoryId, unsorted).map(LowStockProductResponse::from);
    }
}
