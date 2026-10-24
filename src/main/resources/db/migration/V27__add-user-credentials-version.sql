-- Ends a user's sessions at once, not only when their access token expires: each access token carries
-- the version it was issued with (claim cv), and requests with an older one are refused (401). It goes
-- up when the password changes or is reset, the role changes or the account is deactivated. The
-- constant default is a catalog change (no rewrite) and covers the old instance's inserts.
ALTER TABLE app_user ADD COLUMN credentials_version INTEGER NOT NULL DEFAULT 0;
