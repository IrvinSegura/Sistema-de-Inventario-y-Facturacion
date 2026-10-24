-- Supervision counts the day's purchase cancellations every minute (cancelledFrom/cancelledTo on the
-- purchases list). Partial like V9's for sales: cancellations are rare, so the index stays small.
-- CONCURRENTLY in a migration of its own (see V18); it first drops what a failed attempt left, valid or
-- INVALID, so after `flyway repair` it simply runs again.
DROP INDEX CONCURRENTLY IF EXISTS ix_purchase_cancelled_at;
CREATE INDEX CONCURRENTLY ix_purchase_cancelled_at ON purchase (cancelled_at) WHERE cancelled_at IS NOT NULL;
