package com.irvin.inventory.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Only writer of product.stock: each change updates stock and inserts its movement in one statement.
@Repository
@RequiredArgsConstructor
public class InventoryMovementRepository {

    private final JdbcTemplate jdbcTemplate;

    // Returns the new stock, or empty if the product is inactive. Also moves the product's average
    // cost: on the right-hand side of SET, stock and average_cost are the values before this purchase.
    // Without units on hand (or a cost yet) the new units set it.
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalInt recordPurchase(Long productId, int quantity, BigDecimal unitCost, Long purchaseId,
            Long userId) {
        List<Integer> stock = jdbcTemplate.queryForList("""
                WITH updated AS (
                    UPDATE product SET stock = stock + ?,
                        average_cost = CASE
                            WHEN stock = 0 OR average_cost IS NULL THEN ?
                            ELSE ROUND((stock * average_cost + ? * ?) / (stock + ?), 4)
                        END
                    WHERE id = ? AND active RETURNING id, stock
                )
                INSERT INTO inventory_movement (product_id, type, quantity, resulting_stock, purchase_id, user_id)
                SELECT id, ?, ?, stock, ?, ? FROM updated
                RETURNING resulting_stock
                """, Integer.class,
                quantity, unitCost, quantity, unitCost, quantity, productId,
                MovementType.PURCHASE.name(), quantity, purchaseId, userId);
        return stock.isEmpty() ? OptionalInt.empty() : OptionalInt.of(stock.getFirst());
    }

    // Returns the new stock, or empty if stock is insufficient or the product is inactive.
    // "stock >= ?" makes concurrent sales of the last unit serialize on the row: only one wins.
    // The sale line gets the product's average cost read under that same row lock, so a purchase
    // committing meanwhile can't leave it with a stale cost.
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalInt recordSale(Long productId, int quantity, Long saleId, Long userId) {
        List<Integer> stock = jdbcTemplate.queryForList("""
                WITH updated AS (
                    UPDATE product SET stock = stock - ? WHERE id = ? AND active AND stock >= ?
                    RETURNING id, stock, average_cost
                ), costed AS (
                    UPDATE sale_item SET unit_cost = updated.average_cost FROM updated
                    WHERE sale_item.sale_id = ? AND sale_item.product_id = updated.id
                )
                INSERT INTO inventory_movement (product_id, type, quantity, resulting_stock, sale_id, user_id)
                SELECT id, ?, ?, stock, ?, ? FROM updated
                RETURNING resulting_stock
                """, Integer.class,
                quantity, productId, quantity, saleId, MovementType.SALE.name(), -quantity, saleId, userId);
        return stock.isEmpty() ? OptionalInt.empty() : OptionalInt.of(stock.getFirst());
    }

    // No "active" filter: the goods are back on the shelf even if the product was deactivated since the sale.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSaleCancellation(Long productId, int quantity, Long saleId, Long userId) {
        int inserted = jdbcTemplate.update("""
                WITH updated AS (
                    UPDATE product SET stock = stock + ? WHERE id = ? RETURNING id, stock
                )
                INSERT INTO inventory_movement (product_id, type, quantity, resulting_stock, sale_id, user_id)
                SELECT id, ?, ?, stock, ?, ? FROM updated
                """,
                quantity, productId, MovementType.SALE_CANCELLATION.name(), quantity, saleId, userId);
        // Products are never deleted (FK), so a miss is a bug: fail instead of silently losing the stock.
        if (inserted != 1) {
            throw new IllegalStateException("Stock was not returned for product " + productId);
        }
    }

    // Signed correction; returns the movement id, or empty if the product is missing or stock would go
    // negative. No "active" filter: discontinued products still get their remaining units written off.
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalLong recordAdjustment(Long productId, int quantity, String reason, UUID idempotencyKey,
            Long userId) {
        List<Long> id = jdbcTemplate.queryForList("""
                WITH updated AS (
                    UPDATE product SET stock = stock + ? WHERE id = ? AND stock + ? >= 0 RETURNING id, stock
                )
                INSERT INTO inventory_movement
                    (product_id, type, quantity, resulting_stock, reason, idempotency_key, user_id)
                SELECT id, ?, ?, stock, ?, ?, ? FROM updated
                RETURNING id
                """, Long.class,
                quantity, productId, quantity, MovementType.ADJUSTMENT.name(), quantity, reason, idempotencyKey,
                userId);
        return id.isEmpty() ? OptionalLong.empty() : OptionalLong.of(id.getFirst());
    }
}
