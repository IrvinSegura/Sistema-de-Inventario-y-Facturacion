-- Store-wide ledger reads by type (e.g. every adjustment of a month), newest first; id completes the
-- ledger's order (movement_date DESC, id DESC). Per-product reads keep ix_inventory_movement_product_date.
-- CONCURRENTLY builds it without blocking sales on this write-heavy table; Flyway runs this migration
-- outside a transaction, which CONCURRENTLY needs. No IF NOT EXISTS: a failed build leaves an INVALID
-- index, and a retry must fail loudly instead of keeping it.
CREATE INDEX CONCURRENTLY ix_inventory_movement_type_date ON inventory_movement (type, movement_date, id);
