package com.irvin.inventory.stock;

import java.net.URI;
import java.util.UUID;

import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.security.Roles;
import com.irvin.inventory.stock.dto.CreateStockAdjustmentRequest;
import com.irvin.inventory.stock.dto.InventoryMovementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stock", description = "Manual adjustments and the stock ledger. ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/stock-adjustments")
@PreAuthorize(Roles.MANAGERS)
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockService stockService;

    @Operation(summary = "Adjust a product's stock with a reason",
            description = "Positive quantity adds units, negative removes them; stock can't go below zero (409). "
                    + "Also allowed on inactive products, to write off remaining units. Send an Idempotency-Key "
                    + "(UUID) to make retries safe: a repeated key returns the original movement (200).")
    @PostMapping
    public ResponseEntity<InventoryMovementResponse> adjust(
            @Valid @RequestBody CreateStockAdjustmentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        StockService.AdjustmentResult result =
                stockService.adjust(request, idempotencyKey, CurrentUser.from(jwt).id());
        if (!result.created()) {
            return ResponseEntity.ok(result.movement());
        }
        return ResponseEntity.created(URI.create(InventoryMovementController.PATH + "/" + result.movement().id()))
                .body(result.movement());
    }
}
