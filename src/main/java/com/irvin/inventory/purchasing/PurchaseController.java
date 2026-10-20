package com.irvin.inventory.purchasing;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.irvin.inventory.purchasing.dto.CreatePurchaseRequest;
import com.irvin.inventory.purchasing.dto.PurchaseResponse;
import com.irvin.inventory.purchasing.dto.PurchaseSummaryResponse;
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

@Tag(name = "Purchases", description = "Stock in from suppliers. ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize(Roles.MANAGERS)
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "Record a purchase and increase stock",
            description = "Send an Idempotency-Key (UUID) to make retries safe: a repeated key returns "
                    + "the original purchase (200) instead of recording it twice.")
    @PostMapping
    public ResponseEntity<PurchaseResponse> create(
            @Valid @RequestBody CreatePurchaseRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        PurchaseService.CreateResult result =
                purchaseService.create(request, idempotencyKey, Long.valueOf(jwt.getSubject()));
        if (!result.created()) {
            return ResponseEntity.ok(result.purchase());
        }
        return ResponseEntity.created(URI.create("/api/v1/purchases/" + result.purchase().id()))
                .body(result.purchase());
    }

    @Operation(summary = "List purchases, newest first unless sorted",
            description = "Sort by id, purchaseDate, total, supplier or recordedBy.")
    @GetMapping
    public Page<PurchaseSummaryResponse> search(
            @RequestParam(required = false) Long supplierId,
            @Parameter(description = "Inclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable) {
        return purchaseService.search(new PurchaseSearch(supplierId, from, to), pageable);
    }

    @Operation(summary = "Get a purchase with its items")
    @GetMapping("/{id}")
    public PurchaseResponse findById(@PathVariable Long id) {
        return purchaseService.findById(id);
    }
}
