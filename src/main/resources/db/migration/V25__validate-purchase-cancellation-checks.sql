-- VALIDATE takes SHARE UPDATE EXCLUSIVE: it scans the rows while sales and purchases keep writing.
-- Every existing row already met the stricter checks they replace or extend (V22, V23).
ALTER TABLE purchase VALIDATE CONSTRAINT fk_purchase_cancelled_by;
ALTER TABLE purchase VALIDATE CONSTRAINT ck_purchase_status;
ALTER TABLE inventory_movement VALIDATE CONSTRAINT ck_inventory_movement_type;
