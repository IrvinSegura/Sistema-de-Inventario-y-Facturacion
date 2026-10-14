-- Optional Idempotency-Key on manual adjustments: a retried "-5 damaged" must not remove 10 units.
-- Nullable without default: adding it only changes metadata.
ALTER TABLE inventory_movement ADD COLUMN idempotency_key UUID;

-- Only adjustments carry a key; purchases and sales keep theirs on the document.
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_idempotency_key
    CHECK (idempotency_key IS NULL OR type = 'ADJUSTMENT');

-- Partial: sale and purchase movements (the bulk of the table) stay out of the index.
CREATE UNIQUE INDEX uk_inventory_movement_idempotency_key
    ON inventory_movement (idempotency_key) WHERE idempotency_key IS NOT NULL;
