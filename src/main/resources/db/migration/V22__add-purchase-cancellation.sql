-- Purchases can be cancelled: the units go back to the supplier through PURCHASE_CANCEL movements
-- (V23). Schema only, like V16: the status default is a catalog change (PostgreSQL 11+ stores it
-- without rewriting the table) and also covers the old instance's inserts during a deploy, and the
-- constraints are NOT VALID so the table isn't scanned under an ACCESS EXCLUSIVE lock. V25 validates them.
ALTER TABLE purchase
    ADD COLUMN status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN cancelled_at        TIMESTAMPTZ,
    ADD COLUMN cancelled_by        BIGINT,
    ADD COLUMN cancellation_reason VARCHAR(255),
    ADD CONSTRAINT fk_purchase_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) NOT VALID,
    -- Same rule as ck_sale_status: cancellation data is present if and only if it's cancelled.
    ADD CONSTRAINT ck_purchase_status CHECK (
        (status = 'COMPLETED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
                              AND cancellation_reason IS NOT NULL AND btrim(cancellation_reason) <> '')
    ) NOT VALID;
