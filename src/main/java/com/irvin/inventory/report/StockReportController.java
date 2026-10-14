package com.irvin.inventory.report;

import com.irvin.inventory.report.dto.LowStockProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Description lives on SaleReportController; springdoc merges tags with the same name.
@Tag(name = "Reports")
@RestController
@RequestMapping("/api/v1/stock-reports")
@RequiredArgsConstructor
public class StockReportController {

    private final StockReportService stockReportService;

    @Operation(summary = "Active products at or below their minimum stock, most urgent first",
            description = "Ordered by how far each product is below its minimum; the sort parameter is ignored.")
    @GetMapping("/low-stock")
    public Page<LowStockProductResponse> lowStock(
            @RequestParam(required = false) Long categoryId,
            @ParameterObject Pageable pageable) {
        return stockReportService.lowStock(categoryId, pageable);
    }
}
