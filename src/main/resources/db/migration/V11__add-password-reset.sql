-- Optional mobile number to receive password reset codes by SMS, in E.164 (+525512345678).
ALTER TABLE app_user ADD COLUMN phone VARCHAR(16);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$');

-- One-time codes to reset a forgotten password, sent by email or SMS. Only the SHA-256 of the code
-- is stored. A code stops working when it is used, replaced by a newer one, expires or gets too
-- many wrong attempts.
CREATE TABLE password_reset_code (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    code_hash      VARCHAR(64) NOT NULL,
    channel        VARCHAR(10) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    attempts       INTEGER     NOT NULL DEFAULT 0,
    invalidated_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_password_reset_code_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_password_reset_code_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_password_reset_code_attempts CHECK (attempts >= 0)
);

-- Finding a user's live code, and counting their recent requests for the rate limit.
CREATE INDEX ix_password_reset_code_user_created ON password_reset_code (user_id, created_at);
