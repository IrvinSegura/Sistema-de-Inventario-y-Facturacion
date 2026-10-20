-- A product's latest sale (sales by product report) walks this index backwards and stops at the first
-- completed sale, instead of reading the product's whole history. It also serves the product_id
-- foreign key and lookups, so it replaces the single-column index. CONCURRENTLY builds and drops it
-- without blocking sales; Flyway runs this migration outside a transaction, which CONCURRENTLY needs
-- (and spring.flyway.postgresql.transactional-lock=false keeps Flyway's own lock from blocking it).
-- No IF NOT EXISTS: a failed build leaves an INVALID index, and a retry must fail loudly instead of
-- keeping it.
CREATE INDEX CONCURRENTLY ix_sale_item_product_sale ON sale_item (product_id, sale_id);
DROP INDEX CONCURRENTLY ix_sale_item_product;
