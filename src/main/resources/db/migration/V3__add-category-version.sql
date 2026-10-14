-- Optimistic locking so concurrent category edits don't silently overwrite each other.
ALTER TABLE category ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
