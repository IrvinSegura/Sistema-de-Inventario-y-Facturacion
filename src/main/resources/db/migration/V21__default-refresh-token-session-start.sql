-- While a deploy starts the new version, the old one keeps issuing tokens without session_started_at:
-- defaulting to now() counts each of those as a new session instead of failing V20's NOT NULL.
-- Kept afterwards too: a token inserted without it starts a session, which is what a login does.
ALTER TABLE refresh_token ALTER COLUMN session_started_at SET DEFAULT now();
