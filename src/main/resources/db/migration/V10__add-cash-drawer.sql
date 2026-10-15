-- One cash drawer per user and local day (store time zone). A sale needs its seller's drawer for
-- that day to be open; closing it records the counted cash against the expected cash at that moment.
CREATE TABLE cash_drawer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    business_date DATE          NOT NULL,
    opening_float NUMERIC(12,2) NOT NULL,
    opened_at     TIMESTAMPTZ   NOT NULL,
    closed_at     TIMESTAMPTZ,
    closed_by     BIGINT,
    -- Opening float + net cash of the day when the drawer was closed.
    expected_cash NUMERIC(12,2),
    counted_cash  NUMERIC(12,2),
    closing_note  VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Also the index sales use to find (and lock) their seller's drawer.
    CONSTRAINT uk_cash_drawer_user_date UNIQUE (user_id, business_date),
    CONSTRAINT fk_cash_drawer_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_cash_drawer_closed_by FOREIGN KEY (closed_by) REFERENCES app_user (id),
    CONSTRAINT ck_cash_drawer_amounts CHECK (opening_float >= 0 AND counted_cash >= 0),
    -- Closing data is present if and only if the drawer is closed; the note stays optional.
    CONSTRAINT ck_cash_drawer_closing CHECK (
        (closed_at IS NULL AND closed_by IS NULL AND expected_cash IS NULL AND counted_cash IS NULL
                           AND closing_note IS NULL) OR
        (closed_at IS NOT NULL AND closed_by IS NOT NULL AND expected_cash IS NOT NULL AND counted_cash IS NOT NULL)
    )
);

-- One physical drawer per user: the previous day's drawer is closed before a new one opens.
CREATE UNIQUE INDEX uk_cash_drawer_open_user ON cash_drawer (user_id) WHERE closed_at IS NULL;

-- The store-wide cash closing lists every drawer of a day.
CREATE INDEX ix_cash_drawer_business_date ON cash_drawer (business_date);
