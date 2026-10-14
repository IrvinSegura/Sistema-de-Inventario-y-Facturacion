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
}
