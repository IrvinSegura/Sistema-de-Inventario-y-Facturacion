-- Drawers can be shared: a manager opens one, hands over the float and assigns one or more users,
-- one of them responsible for counting and closing it. A personal drawer is the one-member case.

-- user_id was both who opened the drawer and who answers for it.
ALTER TABLE cash_drawer RENAME COLUMN user_id TO responsible_id;
ALTER TABLE cash_drawer RENAME CONSTRAINT fk_cash_drawer_user TO fk_cash_drawer_responsible;
ALTER TABLE cash_drawer ADD COLUMN opened_by BIGINT;
UPDATE cash_drawer SET opened_by = responsible_id;
ALTER TABLE cash_drawer
    ALTER COLUMN opened_by SET NOT NULL,
    ADD CONSTRAINT fk_cash_drawer_opened_by FOREIGN KEY (opened_by) REFERENCES app_user (id),
    -- Target of the members' (drawer, day) foreign key.
    ADD CONSTRAINT uk_cash_drawer_id_date UNIQUE (id, business_date);

-- The per-user rules now apply to every member, not just the responsible.
ALTER TABLE cash_drawer DROP CONSTRAINT uk_cash_drawer_user_date;
DROP INDEX uk_cash_drawer_open_user;

-- Fixed when the drawer opens; only drawer_open changes, when it closes.
CREATE TABLE cash_drawer_member (
    cash_drawer_id BIGINT  NOT NULL,
    user_id        BIGINT  NOT NULL,
    -- Copies of the drawer's day and state, so the per-user rules below are constraints.
    business_date  DATE    NOT NULL,
    drawer_open    BOOLEAN NOT NULL,
    CONSTRAINT pk_cash_drawer_member PRIMARY KEY (cash_drawer_id, user_id),
    -- One drawer per user and day; also the index sales use to find their seller's drawer.
    CONSTRAINT uk_cash_drawer_member_user_date UNIQUE (user_id, business_date),
    CONSTRAINT fk_cash_drawer_member_drawer FOREIGN KEY (cash_drawer_id, business_date)
        REFERENCES cash_drawer (id, business_date),
    CONSTRAINT fk_cash_drawer_member_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- One open drawer per user: the previous day's is closed before they join a new one.
CREATE UNIQUE INDEX uk_cash_drawer_member_open_user ON cash_drawer_member (user_id) WHERE drawer_open;

INSERT INTO cash_drawer_member (cash_drawer_id, user_id, business_date, drawer_open)
SELECT id, responsible_id, business_date, closed_at IS NULL FROM cash_drawer;

-- The drawer a sale's cash went into, and the one its refund came out of. Null for sales made
-- before drawers existed and for cancellations made while the seller had no open drawer.
ALTER TABLE sale
    ADD COLUMN cash_drawer_id BIGINT,
    ADD COLUMN cancellation_drawer_id BIGINT,
    ADD CONSTRAINT fk_sale_cash_drawer FOREIGN KEY (cash_drawer_id) REFERENCES cash_drawer (id),
    ADD CONSTRAINT fk_sale_cancellation_drawer FOREIGN KEY (cancellation_drawer_id) REFERENCES cash_drawer (id),
    ADD CONSTRAINT ck_sale_cancellation_drawer CHECK (cancellation_drawer_id IS NULL OR status = 'CANCELLED');

-- A drawer's expected cash and its tickets.
CREATE INDEX ix_sale_cash_drawer ON sale (cash_drawer_id);
CREATE INDEX ix_sale_cancellation_drawer ON sale (cancellation_drawer_id) WHERE cancellation_drawer_id IS NOT NULL;

-- Until now a user sold only while their own drawer was open, one drawer at a time, so the drawer
-- open when a sale (or cancellation) was stamped is the one of its seller that day.
UPDATE sale s
SET cash_drawer_id = d.id
FROM cash_drawer d
WHERE d.responsible_id = s.user_id
  AND s.sale_date >= d.opened_at
  AND (d.closed_at IS NULL OR s.sale_date < d.closed_at);

UPDATE sale s
SET cancellation_drawer_id = d.id
FROM cash_drawer d
WHERE s.cancelled_at IS NOT NULL
  AND d.responsible_id = s.user_id
  AND s.cancelled_at >= d.opened_at
  AND (d.closed_at IS NULL OR s.cancelled_at < d.closed_at);
