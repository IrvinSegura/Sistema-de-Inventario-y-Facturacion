-- Optimistic locking so concurrent supplier edits don't silently overwrite each other.
ALTER TABLE supplier ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
