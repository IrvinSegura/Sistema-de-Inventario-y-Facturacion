-- Audit trail of account changes: who changed whose email, role, status or password, and when. Written
-- in the same transaction as the change. A new table, so its constraints need no NOT VALID step.
CREATE TABLE account_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The account changed.
    user_id     BIGINT       NOT NULL,
    -- Who did it: an admin, or the user themself (own change, forgotten-password reset).
    actor_id    BIGINT       NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    -- Before and after, when the change has values (email, role).
    old_value   VARCHAR(150),
    new_value   VARCHAR(150),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_account_event_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_account_event_actor FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT ck_account_event_type CHECK (type IN ('CREATED', 'EMAIL_CHANGED', 'ROLE_CHANGED', 'DEACTIVATED',
        'REACTIVATED', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'PASSWORD_RECOVERED'))
);

-- A user's history, newest first.
CREATE INDEX ix_account_event_user_date ON account_event (user_id, occurred_at);
CREATE INDEX ix_account_event_actor ON account_event (actor_id);
