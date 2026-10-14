package com.irvin.inventory.stock;

import java.util.List;
import java.util.OptionalInt;

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

    // Returns the new stock, or empty if the product is inactive.
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalInt recordPurchase(Long productId, int quantity, Long purchaseId, Long userId) {
        List<Integer> stock = jdbcTemplate.queryForList("""
                WITH updated AS (
                    UPDATE product SET stock = stock + ? WHERE id = ? AND active RETURNING id, stock
                )
                INSERT INTO inventory_movement (product_id, type, quantity, resulting_stock, purchase_id, user_id)
                SELECT id, ?, ?, stock, ?, ? FROM updated
                RETURNING resulting_stock
                """, Integer.class,
                quantity, productId, MovementType.PURCHASE.name(), quantity, purchaseId, userId);
        return stock.isEmpty() ? OptionalInt.empty() : OptionalInt.of(stock.getFirst());
    }

    // Returns the new stock, or empty if stock is insufficient or the product is inactive.
    // "stock >= ?" makes concurrent sales of the last unit serialize on the row: only one wins.
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalInt recordSale(Long productId, int quantity, Long saleId, Long userId) {
        List<Integer> stock = jdbcTemplate.queryForList("""
                WITH updated AS (
                    UPDATE product SET stock = stock - ? WHERE id = ? AND active AND stock >= ? RETURNING id, stock
                )
                INSERT INTO inventory_movement (product_id, type, quantity, resulting_stock, sale_id, user_id)
                SELECT id, ?, ?, stock, ?, ? FROM updated
                RETURNING resulting_stock
                """, Integer.class,
                quantity, productId, quantity, MovementType.SALE.name(), -quantity, saleId, userId);
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
}
