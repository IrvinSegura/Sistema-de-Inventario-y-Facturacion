-- Product search uses LIKE '%text%' on lower(name) and lower(sku); trigram indexes avoid full scans.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_product_name_trgm ON product USING gin (lower(name) gin_trgm_ops);
CREATE INDEX ix_product_sku_trgm ON product USING gin (lower(sku) gin_trgm_ops);
