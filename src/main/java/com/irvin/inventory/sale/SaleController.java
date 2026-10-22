package com.irvin.inventory.sale;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.irvin.inventory.sale.dto.CancelSaleRequest;
import com.irvin.inventory.sale.dto.CreateSaleRequest;
import com.irvin.inventory.sale.dto.SaleResponse;
import com.irvin.inventory.sale.dto.SaleSummaryResponse;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sales", description = "Record and view: any user (sellers only see their own). Cancel: ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @Operation(summary = "Record a sale and decrease stock",
            description = "Prices and VAT come from the products and totals are computed by the server. "
                    + "Without customerId the sale goes to the walk-in customer. A repeated Idempotency-Key "
                    + "returns the original sale (200) instead of charging twice. Not enough stock: 409. "
                    + "Only sellers sell, from their open drawer: 403 for managers.")
    @PostMapping
    public ResponseEntity<SaleResponse> create(
            @Valid @RequestBody CreateSaleRequest request,
            @Parameter(description = "UUID generated once per checkout; reuse it when retrying")
            @RequestHeader(name = "Idempotency-Key") UUID idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        SaleService.CreateResult result = saleService.create(request, idempotencyKey, CurrentUser.from(jwt));
        if (!result.created()) {
            return ResponseEntity.ok(result.sale());
        }
        return ResponseEntity.created(URI.create("/api/v1/sales/" + result.sale().id())).body(result.sale());
    }

    @Operation(summary = "List sales, newest first unless sorted",
            description = "Sellers only get their own sales. Sort by saleNumber, saleDate, total, paymentMethod, "
                    + "status, customer or seller.")
    @GetMapping
    public Page<SaleSummaryResponse> search(
            @RequestParam(required = false) Long saleNumber,
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Ignored for sellers") @RequestParam(required = false) Long sellerId,
            @Parameter(description = "Sales recorded in this cash drawer; sellers still get only their own")
            @RequestParam(required = false) Long cashDrawerId,
            @RequestParam(required = false) SaleStatus status,
            @Parameter(description = "Inclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Cancelled at or after this ISO-8601 instant (only cancelled sales match)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cancelledFrom,
            @Parameter(description = "Cancelled before this ISO-8601 instant (only cancelled sales match)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cancelledTo,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return saleService.search(new SaleSearch(saleNumber, customerId, sellerId, cashDrawerId, status, from, to,
                cancelledFrom, cancelledTo), pageable, CurrentUser.from(jwt));
    }

    @Operation(summary = "Get a sale with its items")
    @GetMapping("/{id}")
    public SaleResponse findById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return saleService.findById(id, CurrentUser.from(jwt));
    }

    @Operation(summary = "Cancel a sale and return its stock",
            description = "A reason is required. Cancelling an already cancelled sale returns 409.")
    @PreAuthorize(Roles.MANAGERS)
    @PostMapping("/{id}/cancellation")
    public SaleResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelSaleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return saleService.cancel(id, request, CurrentUser.from(jwt).id());
    }
}
