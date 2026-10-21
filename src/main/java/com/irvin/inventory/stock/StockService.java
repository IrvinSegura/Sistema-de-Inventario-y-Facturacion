package com.irvin.inventory.stock;

import java.util.Optional;
import java.util.UUID;

import com.irvin.inventory.catalog.ProductRepository;
import com.irvin.inventory.common.IdempotencyLock;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.stock.dto.CreateStockAdjustmentRequest;
import com.irvin.inventory.stock.dto.InventoryMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Lock order: idempotency key -> product row (a single one per adjustment).
@Service
@RequiredArgsConstructor
public class StockService {

    private static final int SEARCH_TIMEOUT_SECONDS = 10;

    private static final String IDEMPOTENCY_SCOPE = "stock-adjustment";

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementQueryRepository inventoryMovementQueryRepository;
    private final ProductRepository productRepository;
    private final IdempotencyLock idempotencyLock;

    public record AdjustmentResult(InventoryMovementResponse movement, boolean created) {
    }

    // The adjustment is one atomic statement on the product row, so it can't interleave with sales.
    @Transactional
    public AdjustmentResult adjust(CreateStockAdjustmentRequest request, UUID idempotencyKey, Long userId) {
        if (request.quantity() == 0) {
            throw new BadRequestException("Quantity must not be zero");
        }
        if (idempotencyKey != null) {
            idempotencyLock.acquire(IDEMPOTENCY_SCOPE, idempotencyKey);
            Optional<InventoryMovementResponse> existing =
                    inventoryMovementQueryRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                if (!isSameRequest(existing.get(), request)) {
                    throw new ConflictException("Idempotency-Key was already used with a different request");
                }
                return new AdjustmentResult(existing.get(), false);
            }
        }
        Long productId = request.productId();
        long movementId = inventoryMovementRepository
                .recordAdjustment(productId, request.quantity(), request.reason(), idempotencyKey, userId)
                // Products are never deleted, so the extra lookup is only needed to explain a failure.
                .orElseThrow(() -> productRepository.existsById(productId)
                        ? new ConflictException("Insufficient stock for product: " + productId)
                        : new BadRequestException("Product not found: " + productId));
        return new AdjustmentResult(inventoryMovementQueryRepository.findById(movementId).orElseThrow(), true);
    }

    // 404 rather than an empty page when a productId names no product: it identifies the ledger being read.
    // One REPEATABLE READ snapshot, so the page and its total count agree while sales come in.
    // Client sort is dropped: a ledger only makes sense in time order. The timeout (a statement timeout
    // through JdbcTemplate) keeps a slow read from holding a connection the checkout needs, like the reports.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = SEARCH_TIMEOUT_SECONDS)
    public Page<InventoryMovementResponse> search(InventoryMovementSearch search, Pageable pageable) {
        if (search.productId() != null && !productRepository.existsById(search.productId())) {
            throw new NotFoundException("Product not found: " + search.productId());
        }
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return inventoryMovementQueryRepository.search(search, unsorted);
    }

    @Transactional(readOnly = true)
    public InventoryMovementResponse findById(Long id) {
        return inventoryMovementQueryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory movement not found"));
    }

    // A retried request must be identical; reusing a key for different data is a client bug.
    private static boolean isSameRequest(InventoryMovementResponse movement, CreateStockAdjustmentRequest request) {
        return movement.productId().equals(request.productId())
                && movement.quantity() == request.quantity()
                && movement.reason().equals(request.reason());
    }
}
