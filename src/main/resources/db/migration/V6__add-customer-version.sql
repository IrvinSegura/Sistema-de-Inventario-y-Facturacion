-- Optimistic locking so concurrent customer edits don't silently overwrite each other.
ALTER TABLE customer ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
