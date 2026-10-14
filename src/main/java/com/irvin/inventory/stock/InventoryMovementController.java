package com.irvin.inventory.stock;

import java.time.Instant;

import com.irvin.inventory.stock.dto.InventoryMovementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Description lives on StockAdjustmentController; springdoc merges tags with the same name.
@Tag(name = "Stock")
@RestController
@RequestMapping(InventoryMovementController.PATH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InventoryMovementController {

    // Also used by StockAdjustmentController for the Location header of new movements.
    static final String PATH = "/api/v1/inventory-movements";

    private final StockService stockService;

    @Operation(summary = "Stock ledger of a product, newest first",
            description = "Every purchase, sale, cancellation and adjustment with the resulting stock. "
                    + "The sort parameter is ignored. When paging through a busy product, send the 'to' of the "
                    + "first request on every page so new movements don't shift the pages.")
    @GetMapping
    public Page<InventoryMovementResponse> search(
            @RequestParam Long productId,
            @RequestParam(required = false) MovementType type,
            @Parameter(description = "Inclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable) {
        return stockService.search(new InventoryMovementSearch(productId, type, from, to), pageable);
    }

    @Operation(summary = "Get an inventory movement")
    @GetMapping("/{id}")
    public InventoryMovementResponse findById(@PathVariable Long id) {
        return stockService.findById(id);
    }
}
