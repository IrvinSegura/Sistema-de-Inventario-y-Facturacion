-- A purchase cancellation takes stock out and points to its purchase. Its own migration, so the
-- ACCESS EXCLUSIVE lock on this write-heavy table is held alone and only for a catalog change: the
-- check is NOT VALID (V25 validates it) and the name fits VARCHAR(20), since changing the column's
-- type would rebuild the partial indexes on it.
ALTER TABLE inventory_movement
    DROP CONSTRAINT ck_inventory_movement_type,
    ADD CONSTRAINT ck_inventory_movement_type CHECK (
        (type = 'PURCHASE'          AND quantity > 0  AND purchase_id IS NOT NULL AND sale_id IS NULL) OR
        (type = 'PURCHASE_CANCEL'   AND quantity < 0  AND purchase_id IS NOT NULL AND sale_id IS NULL) OR
        (type = 'SALE'              AND quantity < 0  AND sale_id IS NOT NULL     AND purchase_id IS NULL) OR
        (type = 'SALE_CANCELLATION' AND quantity > 0  AND sale_id IS NOT NULL     AND purchase_id IS NULL) OR
        (type = 'ADJUSTMENT'        AND quantity <> 0 AND purchase_id IS NULL     AND sale_id IS NULL
                                    AND reason IS NOT NULL AND btrim(reason) <> '')
    ) NOT VALID;
