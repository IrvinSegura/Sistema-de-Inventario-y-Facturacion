-- Double clicks at the checkout are common: every sale must carry an Idempotency-Key.
-- Purchases keep it optional (recorded only by admins).
ALTER TABLE sale ALTER COLUMN idempotency_key SET NOT NULL;
