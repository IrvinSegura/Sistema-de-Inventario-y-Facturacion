-- One movement per purchase line and type, like sales: a purchase line now has its PURCHASE movement
-- and, once cancelled, its PURCHASE_CANCEL one, and can't be cancelled twice. Replaces the
-- (purchase_id, product_id) index, which would reject the cancellation. Built and dropped
-- CONCURRENTLY on this write-heavy table, outside a transaction (see V18). No IF NOT EXISTS: a failed
-- build leaves an INVALID index, and a retry must fail loudly instead of keeping it.
CREATE UNIQUE INDEX CONCURRENTLY uk_inventory_movement_purchase_product_type
    ON inventory_movement (purchase_id, product_id, type) WHERE purchase_id IS NOT NULL;
DROP INDEX CONCURRENTLY uk_inventory_movement_purchase_product;
