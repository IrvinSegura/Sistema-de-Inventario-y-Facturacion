-- One movement per purchase line and type, like sales: a purchase line now has its PURCHASE movement
-- and, once cancelled, its PURCHASE_CANCEL one, and can't be cancelled twice. Replaces the
-- (purchase_id, product_id) index, which would reject the cancellation. Built and dropped
-- CONCURRENTLY on this write-heavy table, outside a transaction (see V18).
-- Safe to run again after a failure (see "Recovering a failed migration" in docs/data-model.md): it
-- first drops what a failed attempt may have left, valid or INVALID, so it never keeps a half-built
-- index. While it rebuilds, the old index keeps purchases unique, unless that attempt had dropped it.
DROP INDEX CONCURRENTLY IF EXISTS uk_inventory_movement_purchase_product_type;
CREATE UNIQUE INDEX CONCURRENTLY uk_inventory_movement_purchase_product_type
    ON inventory_movement (purchase_id, product_id, type) WHERE purchase_id IS NOT NULL;
DROP INDEX CONCURRENTLY IF EXISTS uk_inventory_movement_purchase_product;
