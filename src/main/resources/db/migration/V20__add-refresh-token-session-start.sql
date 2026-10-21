-- A session ends app.jwt.max-session-age after its login, however often it refreshes: every rotated
-- token carries the instant of the login it descends from. Tokens issued before this count from
-- their own creation. The table only holds live and recently expired tokens, so the rewrite is small.
ALTER TABLE refresh_token ADD COLUMN session_started_at TIMESTAMPTZ;
UPDATE refresh_token SET session_started_at = created_at;
ALTER TABLE refresh_token ALTER COLUMN session_started_at SET NOT NULL;
